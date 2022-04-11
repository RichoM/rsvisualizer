(ns rsvisualizer.pixi
  (:require [clojure.core.async :as a :refer [go <!]]
            [oops.core :refer [oget oset! ocall!]]
            [utils.pixi :as pixi]
            [utils.core :as u]))

(defonce pixi (atom nil))

(def ^:const YELLOW_REGULAR 0xffff00)
(def ^:const YELLOW_HIGHLIGHT 0xffba00)
(def ^:const BLUE_REGULAR 0x00aaff)
(def ^:const BLUE_HIGHLIGHT 0x00ffff)
(def ^:const GRAY_REGULAR 0xaaaaaa)
(def ^:const GRAY_HIGHLIGHT 0xdddddd)

(def ^:const PIXEL_TO_WORLD (/ 1.5 864))

(defn pixel->world [[x y]]
  [(* x PIXEL_TO_WORLD)
   (* y PIXEL_TO_WORLD -1)])

(defn world->pixel [[x y]]
  [(/ x PIXEL_TO_WORLD)
   (/ y PIXEL_TO_WORLD -1)])

(defn resize-field []
  (when-let [{:keys [html app field]} @pixi]
    (doto html
      (oset! :style.height
             (u/format "calc(100% - %1px)"
                       (+ 30 (oget (js/document.querySelector "#top-bar")
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
  (go {:field (<! (pixi/load-texture! "imgs/field.png"))
       :ball (<! (pixi/load-texture! "imgs/ball.png"))
       :robot (<! (pixi/load-texture! "imgs/robot.png"))
       :cross (<! (pixi/load-texture! "imgs/cross.png"))}))


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
                            pixel-coords [(oget position :x) (oget position :y)]
                            world-coords (pixel->world pixel-coords)]
                        (swap! state-atom assoc :cursor {:pixel pixel-coords
                                                         :world world-coords})))))))

(defn initialize-previous-ball! [field {ball-texture :ball}]
  (doto (pixi/make-sprite! ball-texture)
    (oset! :alpha 0.5)
    (pixi/set-position! [0 0])
    (pixi/add-to! field)))

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
                                               #(pixi/set-position! label [(oget robot :x)
                                                                           (oget robot :y)]))))]
    (mapv (fn [idx]
            (doto (pixi/make-sprite! robot-texture)
              (oset! :tint GRAY_REGULAR)
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
                                                    (/ (oget field :height) -2))
                                               (+ (oget robot :y) 40)
                                               (+ (oget robot :y) -40))]
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

(defn initialize-cursor! [state-atom app field]
  (let [label-style (js/PIXI.TextStyle. (clj->js {:fontFamily "monospace"
                                                  :fontSize 22
                                                  :stroke "#ffffff"
                                                  :fill "#656565"
                                                  :strokeThickness 5}))
        label (doto (pixi/make-label! "" label-style)
                (oset! :anchor.x 0.0))]
    (pixi/add-child! field label)
    (pixi/add-ticker! app #(when-let [{[wx wy] :world [px py] :pixel}
                                      (-> @state-atom :cursor)]
                             (oset! label :text (u/format "[%1 %2]"
                                                          (.toFixed wx 3)
                                                          (.toFixed wy 3)))
                             (pixi/set-position! label [(+ px 12) py])))
    label))

(defn initialize! [state-atom]
  (go (let [html (js/document.getElementById "field-panel")
            app (pixi/make-application! html)
            textures (<! (load-textures!))
            field (initialize-field! state-atom app textures)
            previous-ball (initialize-previous-ball! field textures)
            future-balls (initialize-future-balls! field textures)
            ball (initialize-ball! field textures)
            robots (initialize-robots! state-atom app field textures)
            roles (initialize-roles! app field robots)
            targets (initialize-targets! app field robots textures)
            cursor (initialize-cursor! state-atom app field)]
        (reset! pixi
                {:app app
                 :html html
                 :field field
                 :robots robots
                 :roles roles
                 :targets targets
                 :ball ball
                 :previous-ball previous-ball
                 :future-balls future-balls
                 :cursor cursor})
        (.addEventListener js/window "resize" resize-field)
        (resize-field))))

(defn update! [new-state]
  (when-let [{:keys [ball previous-ball future-balls robots roles targets]} @pixi]
    (when-let [{:keys [robot] :as snapshot} (-> new-state :strategy :snapshot)]
      (when (or (nil? (-> new-state :selected-robot))
                (= robot (-> new-state :selected-robot)))
        (when-let [{:keys [x y stale-time previous future]} (snapshot :ball)]
          (doto ball
            (oset! :tint (if (< stale-time 0.1) 0x00ff00 0xaaaaaa))
            (pixi/set-position! (world->pixel [x y])))
          (if-let [{:keys [x y]} previous]
            (doto previous-ball
              (oset! :visible (-> new-state :settings :ball-prediction?))
              (pixi/set-position! (world->pixel [x y])))
            (oset! previous-ball :visible false))
          (doseq [[idx future-ball] (map-indexed vector future-balls)]
            (if-let [{:keys [x y]} (nth future idx nil)]
              (doto future-ball
                (oset! :visible (-> new-state :settings :ball-prediction?))
                (pixi/set-position! (world->pixel [x y])))
              (oset! future-ball :visible false))))
        (doseq [[idx {:keys [x y a target role flipped?]}] (map-indexed vector (snapshot :robots))]
          (oset! (nth robots idx) :tint
                 (let [color (-> new-state :strategy :snapshot :color)
                       selected? (= idx (-> new-state :selected-robot))]
                   (if flipped?
                     (if selected? GRAY_HIGHLIGHT GRAY_REGULAR)
                     (case color
                       "Y" (if selected? YELLOW_HIGHLIGHT YELLOW_REGULAR)
                       "B" (if selected? BLUE_HIGHLIGHT BLUE_REGULAR)
                       (if selected? GRAY_HIGHLIGHT GRAY_REGULAR)))))
          (doto (nth robots idx)
            (pixi/set-position! (world->pixel [x y]))
            (pixi/set-rotation! (* -1 a)))
          (if-let [{{tx :x ty :y} :point} target]
            (doto (nth targets idx)
              (oset! :visible true)
              (pixi/set-position! (world->pixel [tx ty])))
            (oset! (nth targets idx) :visible false))
          (oset! (nth roles idx) :text (get role :name "")))))))

(defn terminate! []
  (try
    (let [[old _] (reset-vals! pixi nil)]
      (when-let [app (:app old)]
        (ocall! app :destroy true true)))
    (catch :default err
      (print "ERROR" err))))

(comment

  (def state-atom rsvisualizer.main/state)
  (-> @state-atom :strategy :snapshot :color)
  (-> @state-atom :selected-robot)
  (swap! state-atom assoc :selected-robot nil)
  (def app (-> @pixi :app))
  (def field (-> @pixi :field))
  (def robots (-> @pixi :robots))
  (def ball (-> @pixi :ball))
  (def cursor (-> @pixi :cursor))
  
  )