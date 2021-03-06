(ns rsvisualizer.pixi
  (:require [clojure.core.async :as a :refer [go <!]]
            [oops.core :refer [oget oset! ocall!]]
            [utils.pixi :as pixi]
            [utils.core :as u]
            [rsvisualizer.history :as h]))

(defonce pixi (atom nil))

(def robot-colors {"Y" {:regular 0xffff00
                        :highlight 0xffba00}
                   "B" {:regular 0x00aaff
                        :highlight 0x00ffff}
                   nil {:regular 0xaaaaaa
                        :highlight 0xdddddd}})

(def ^:const PIXEL_TO_WORLD (/ 1.5 864))

(defn pixel->world [[x y] better-coord-system?]
  (let [wx (* x PIXEL_TO_WORLD)
        wy (* y PIXEL_TO_WORLD -1)]
    (if better-coord-system?
      [wx wy]
      [(* -1 wy) wx])))

(defn world->pixel [[x y] better-coord-system?]
  (let [px (/ x PIXEL_TO_WORLD)
        py (/ y PIXEL_TO_WORLD -1)]
    (if better-coord-system?
      [px py]
      [(* -1 py) px])))

(defn predict-movement [{:keys [x y velocity]} t]
  (let [{vx :x vy :y} velocity]
    [(+ x (* vx t))
     (+ y (* vy t))]))

(defn get-selected-snapshot
  [{:keys [selected-snapshot history strategy]}]
  (:snapshot (if selected-snapshot
               (h/get history selected-snapshot)
               strategy)))

(defn resize-field []
  (when-let [{:keys [html app field]} @pixi]
    (doto html
      (oset! :style.height
             (u/format "calc(100% - %1px)"
                       (+ 10
                          (oget (js/document.querySelector "#top-bar")
                                :offsetHeight)
                          (oget (js/document.querySelector "#bottom-bar")
                                :offsetHeight))))
      (oset! :style.width
             (u/format "calc(100% - %1px)"
                       (+ 30 (oget (js/document.querySelector "#side-bar")
                                   :offsetWidth)))))
    (ocall! app :resize)
    (doto field
      (pixi/set-height! (- (oget app :screen.height) 15))
      (pixi/set-position! (pixi/get-screen-center app)))))

(defn load-textures! []
  (go (let [[field ball robot cross rotate]
            (<! (->> ["imgs/field.png"
                      "imgs/ball.png"
                      "imgs/robot.png"
                      "imgs/cross.png"
                      "imgs/rotate.png"]
                     (map pixi/load-texture!)
                     (a/map vector)))]
        {:field field
         :ball ball
         :robot robot
         :cross cross
         :rotate rotate})))

(defn initialize-field! [state-atom app {field-texture :field}]
  (let [field (pixi/make-sprite! field-texture)]
    (doto field
      (pixi/set-position! (pixi/get-screen-center app))
      (pixi/add-to! (oget app :stage))
      (oset! :interactive true)
      (ocall! :on "click" (fn [e]
                            (ocall! e :stopPropagation)
                            (swap! state-atom assoc :selected-robot nil)))
      (ocall! :on "mousemove"
              (fn [e] (let [position (ocall! e :data.getLocalPosition field)
                            pixel-coords [(oget position :x) (oget position :y)]]
                        (swap! state-atom assoc :cursor pixel-coords)))))))

(defn initialize-future-balls! [field {ball-texture :ball}]
  (mapv (fn [idx]
          (doto (pixi/make-sprite! ball-texture)
            (oset! :alpha (+ 0.15 (* idx (/ -0.1 15))))
            (oset! :tint 0x0000ff)
            (pixi/set-position! [0 0])
            (pixi/add-to! field)))
        (range 15)))

(defn initialize-ball! [field {ball-texture :ball}]
  (doto (pixi/make-sprite! ball-texture)
    (oset! :tint 0x00ff00)
    (pixi/set-position! [0 0])
    (pixi/add-to! field)))

(defn initialize-robots! [state-atom app field {robot-texture :robot}]
  (let [label-style (js/PIXI.TextStyle. (clj->js {:fontFamily "sans-serif"
                                                  :fontSize 26
                                                  :fontWeight "bold"
                                                  :fill "#ffffff"
                                                  :stroke "#000000"
                                                  :strokeThickness 4}))
        add-robot-label! (fn [robot idx]
                           (let [label (pixi/make-label! idx label-style)]
                             (pixi/add-child! field label)
                             (pixi/add-ticker! app
                                               #(doto label
                                                  (oset! :visible (oget robot :visible))
                                                  (pixi/set-position! [(oget robot :x)
                                                                       (oget robot :y)])))))]
    (mapv (fn [idx]
            (doto (pixi/make-sprite! robot-texture)
              (oset! :tint (get-in robot-colors [nil :regular]))
              (oset! :interactive true)
              (ocall! :on "click" (fn [e]
                                    (ocall! e :stopPropagation)
                                    (swap! state-atom update :selected-robot
                                           #(if (= % idx) nil idx))))
              (pixi/set-position! [(* 50 (dec idx)) -50])
              (pixi/add-to! field)
              (add-robot-label! (inc idx))))
          (range 3))))

(defn initialize-roles! [app field robots]
  (let [label-style (js/PIXI.TextStyle. (clj->js {:fontFamily "sans-serif"
                                                  :fontSize 16
                                                  :fill "#000000"
                                                  :stroke "#ffffff"
                                                  :strokeThickness 2}))]
    (mapv (fn [robot]
            (let [label (doto (pixi/make-label! "" label-style)
                          (pixi/add-to! field))]
              (pixi/add-ticker! app #(let [x (oget robot :x)
                                           y (if (< (oget robot :y)
                                                    (/ (oget field :height) -3))
                                               (+ (oget robot :y) 40)
                                               (- (oget robot :y) 40))]
                                       (pixi/set-position! label [x y])))
              label))
          robots)))

(defn initialize-targets! [app field robots {cross-texture :cross}]
  (mapv (fn [robot]
          (let [target (doto (pixi/make-sprite! cross-texture)
                         (oset! :visible false)
                         (pixi/add-to! field))
                line (doto (js/PIXI.Graphics.)
                       (pixi/add-to! field))]
            (pixi/add-ticker! app #(if (oget target :visible)
                                     (doto line
                                       (ocall! :clear)
                                       (ocall! :lineStyle (clj->js {:width 2
                                                                    :color 0x5555ff
                                                                    :alpha 0.5}))
                                       (pixi/draw-dashed-line! [(oget robot :x) (oget robot :y)]
                                                               [(oget target :x) (oget target :y)]
                                                               :gap 5)
                                       (ocall! :closePath)
                                       (oset! :visible true))
                                     (oset! line :visible false)))
            target))
        robots))

(defn initialize-rotators! [app field robots {rotate-texture :rotate}]
  (mapv (fn [robot]
          (let [rotator (doto (pixi/make-sprite! rotate-texture)
                         (oset! :visible false)
                         (pixi/add-to! field))]
            (pixi/add-ticker! app #(pixi/set-position! rotator [(oget robot :x)
                                                                (oget robot :y)]))
            rotator))
        robots))

(defn initialize-cursor! [state-atom app field]
  (let [label-style (js/PIXI.TextStyle. (clj->js {:fontFamily "monospace"
                                                  :fontSize 22
                                                  :stroke "#ffffff"
                                                  :fill "#656565"
                                                  :strokeThickness 5}))
        label (doto (pixi/make-label! "" label-style)
                (oset! :anchor.x 0.0))]
    (pixi/add-child! field label)
    (pixi/add-ticker! app #(when-let [[px py] (-> @state-atom :cursor)]
                             (let [[x y] (pixel->world [px py] (-> @state-atom :settings :better-coord-system?))]
                               (oset! label :text (u/format "[%1 %2]"
                                                            (.toFixed x 3)
                                                            (.toFixed y 3))))
                             (pixi/set-position! label [(+ px 12) py])))
    label))

(defn initialize! [state-atom]
  (go (let [html (js/document.getElementById "field-panel")
            app (pixi/make-application! (js/document.getElementById "canvas-panel"))
            textures (<! (load-textures!))
            field (initialize-field! state-atom app textures)
            future-balls (initialize-future-balls! field textures)
            ball (initialize-ball! field textures)
            robots (initialize-robots! state-atom app field textures)
            roles (initialize-roles! app field robots)
            targets (initialize-targets! app field robots textures)
            rotators (initialize-rotators! app field robots textures)
            cursor (initialize-cursor! state-atom app field)]
        (reset! pixi
                {:app app
                 :html html
                 :field field
                 :robots robots
                 :roles roles
                 :targets targets
                 :rotators rotators
                 :ball ball
                 :future-balls future-balls
                 :cursor cursor})
        (.addEventListener js/window "resize" resize-field)
        (resize-field))))

(defn find-latest-robot-data 
  [{:keys [selected-snapshot history]} robot-idx]
  (loop [i (or selected-snapshot (dec (h/count history)))]
    (when (pos? i)
      (if-let [robot (get-in (h/get history i) [:snapshot :robots robot-idx])]
        robot
        (recur (dec i))))))

(defn update-robot!
  [idx robots targets rotators roles robot-data
   ghost? better-coord-system?]
  (let [{:keys [x y a action role wheels]} robot-data
        visible? (and x y a)]
    (doto (nth robots idx)
      (oset! :visible visible?)
      (oset! :alpha (if ghost? 0.5 1))
      (pixi/set-position! (world->pixel [x y] better-coord-system?))
      (pixi/set-rotation! (* -1 a)))
    (if-let [{tx :x ty :y} (:target action)]
      (doto (nth targets idx)
        (oset! :visible visible?)
        (pixi/set-position! (world->pixel [tx ty] better-coord-system?)))
      (oset! (nth targets idx) :visible false))
    (let [angle (:angle action)
          opposite-angle #(mod (+ % Math/PI) (* Math/PI 2))]
      (if (and angle
               (> (Math/abs (- angle a)) 0.02)
               (> (Math/abs (- angle (opposite-angle a))) 0.02))
        (doto (nth rotators idx)
          (oset! :visible visible?)
          (oset! :scale.x (let [[vl vr] wheels]
                            (if (< vl vr) -1 1))))
        (oset! (nth rotators idx) :visible false)))
    (doto (nth roles idx)
      (oset! :visible visible?)
      (oset! :text (get role :name "")))))

(defn update-snapshot! [new-state]
  (when-let [{:keys [ball future-balls robots roles targets rotators]} @pixi]
    (let [snapshot (get-selected-snapshot new-state)
          better-coord-system? (-> new-state :settings :better-coord-system?)]
      (dotimes [idx 3]
        (oset! (nth robots idx) :tint
               (let [color (:color snapshot)
                     selected? (= idx (-> new-state :selected-robot))
                     flipped? (-> snapshot :robots (get idx) :flipped?)]
                 (get-in robot-colors [(if flipped? nil color)
                                       (if selected? :highlight :regular)]))))
      (if-let [{:keys [robot]} snapshot]
        (when (or (nil? (-> new-state :selected-robot))
                  (= robot (-> new-state :selected-robot)))
          (when-let [{:keys [x y stale-time]} (:ball snapshot)]
            (doto ball
              (oset! :visible true)
              (oset! :tint (if (< stale-time 0.1) 0x00ff00 0xaaaaaa))
              (pixi/set-position! (world->pixel [x y] better-coord-system?)))
            (doseq [[idx future-ball] (map-indexed vector future-balls)]
              (if (-> new-state :settings :ball-prediction?)
                (doto future-ball
                  (oset! :visible true)
                  (pixi/set-position! (world->pixel
                                       (predict-movement (:ball snapshot)
                                                         (* 0.128 (inc idx)))
                                       better-coord-system?)))
                (oset! future-ball :visible false))))
          (doseq [[idx robot-data] (map-indexed vector (:robots snapshot))]
            (let [ghost? (and (nil? robot-data)
                              (-> new-state :settings :ghost-robots?))
                  robot-data (if ghost?
                               (find-latest-robot-data new-state idx)
                               robot-data)]
              (update-robot! idx robots targets rotators roles
                             robot-data ghost? better-coord-system?))))
        (when (pos? (h/count (:history new-state)))
          (doseq [pixi-obj (flatten [ball future-balls robots roles targets rotators])]
            (oset! pixi-obj :visible false)))))))

(defn terminate! []
  (try
    (let [[old _] (reset-vals! pixi nil)]
      (when-let [app (:app old)]
        (ocall! app :destroy true true)))
    (catch :default err
      (print "ERROR" err))))

(comment

  (def state-atom rsvisualizer.main/state)
  (def new-state @state-atom)
  (-> @state-atom :selected-snapshot)
  (-> @state-atom :history)
  (-> @state-atom :strategy :snapshot :color)
  (-> @state-atom :selected-robot)
  (swap! state-atom assoc :selected-robot nil)
  (def app (-> @pixi :app))
  (def field (-> @pixi :field))
  (def robots (-> @pixi :robots))
  (def ball (-> @pixi :ball))
  (def cursor (-> @pixi :cursor))
  
  )