(ns rsvisualizer.ui
  (:require [clojure.core.async :as a :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [oops.core :refer [oget oset! ocall!]]
            [crate.core :as crate]
            [utils.pixi :as pixi]
            [utils.bootstrap :as b]
            [utils.core :as u]))

(defonce pixi (atom nil))
(defonce updates (atom nil))

(def ^:const PIXEL_TO_WORLD (/ 1.5 864))

(defn pixel->world [[x y]]
  [(* x PIXEL_TO_WORLD)
   (* y PIXEL_TO_WORLD -1)])

(defn world->pixel [[x y]]
  [(/ x PIXEL_TO_WORLD)
   (/ y PIXEL_TO_WORLD -1)])

(defn main-container []
  (crate/html
   [:div.container-fluid
    [:div.row
     [:div#side-bar.col-auto
      [:h3.my-1.text-center "RS Visualizer"]
      [:div.row.my-1]
      [:div.d-grid
       [:button#r0-button.r-button.btn.btn-sm.btn-outline-dark.rounded-pill
        {:type "button" :data-bs-toggle "button"}
        [:i.fa-solid.fa-cube.me-2]
        "Robot 1"]]
      [:div.row.my-1]
      [:div.d-grid
       [:button#r1-button.r-button.btn.btn-sm.btn-outline-dark.rounded-pill
        {:type "button" :data-bs-toggle "button"}
        [:i.fa-solid.fa-cube.me-2]
        "Robot 2"]]
      [:div.row.my-1]
      [:div.d-grid
       [:button#r2-button.r-button.btn.btn-sm.btn-outline-dark.rounded-pill
        {:type "button" :data-bs-toggle "button"}
        [:i.fa-solid.fa-cube.me-2]
        "Robot 3"]]
      [:div.row.my-2]
      [:div.form-check.form-switch.text-center.mx-3
       [:input#ball-prediction.form-check-input {:type "checkbox" :role "switch" :checked true}]
       [:label.form-check-.ebal {:for "ball-prediction"} "Ball prediction?"]]]
     [:div.col
      [:div#top-bar.row.text-center.py-1
       [:div.col]
       [:div.col-3
        [:div.d-grid
         [:button.btn.btn-sm.btn-outline-dark.rounded-pill {:type "button"}
          [:i.fa-solid.fa-ruler.me-2]
          "Measure"]]]
       [:div.col-3
        [:div.d-grid
         [:button.btn.btn-sm.btn-outline-dark.rounded-pill {:type "button"}
          [:i.fa-solid.fa-arrows-up-down-left-right.me-2]
          "Move"]]]
       [:div.col]]
      [:div#field-panel
       [:div#mouse-pos.position-absolute.start-0.top-0.font-monospace]]]]]
    ))

(defn modal-container []
  (crate/html [:div#modal-dialogs]))

(defn toast-container []
  (crate/html [:div#toast-container.toast-container.position-absolute.end-0.top-0.p-3]))

(defn initialize-main-ui! [state-atom]
  (doto js/document.body
    (oset! :innerHTML "")
    (.appendChild (main-container))
    (.appendChild (modal-container))
    (.appendChild (toast-container)))
  (let [toggle-selection (fn [new] (fn [old] (if (= old new) nil new)))]
    (doseq [[idx selector] (map-indexed vector ["r0-button" "r1-button" "r2-button"])]
      (b/on-click (js/document.getElementById selector)
                  #(swap! state-atom update :selected-robot (toggle-selection idx))))))
    
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
      (pixi/set-height! (oget app :screen.height))
      (pixi/set-position! (pixi/get-screen-center app)))))

(defn initialize-pixi! [state-atom]
  (go (let [html (js/document.getElementById "field-panel")
            app (pixi/make-application! html)
            field (doto (pixi/make-sprite! (<! (pixi/load-texture! "imgs/field.png")))
                    (pixi/set-position! (pixi/get-screen-center app))
                    (pixi/add-to! (oget app :stage)))
            ball (doto (pixi/make-sprite! (<! (pixi/load-texture! "imgs/ball.png")))
                   (oset! :tint 0x00ff00)
                   (pixi/set-position! [0 0])
                   (pixi/add-to! field))
            robots (let [robot-texture (<! (pixi/load-texture! "imgs/robot.png"))
                         label-style (js/PIXI.TextStyle.  (clj->js {:fontFamily "sans-serif" ;"DatalegreyaThin"
                                                                    :fontSize 26
                                                                    :fontWeight "bold"
                                                                    :fill "#ffffff"
                                                                    :stroke "#000000"
                                                                    :strokeThickness 4}))]
                     (mapv (fn [idx]
                             (let [add-robot-label! (fn [robot idx]
                                                      (let [
                                                            label (js/PIXI.Text. (str idx) label-style)
                                                            ticker #(doto label
                                                                      (oset! :x (oget robot :x))
                                                                      (oset! :y (+ (oget robot :y) 0)))]
                                                        (doto label
                                                          (oset! :anchor.x 0.5)
                                                          (oset! :anchor.y 0.5)
                                                          (pixi/add-to! field))
                                                        (pixi/add-ticker! app ticker)))]
                               (doto (pixi/make-sprite! robot-texture)
                                 (oset! :tint 0x00aaff)
                                 (oset! :interactive true)
                                 (ocall! :on "click" (fn [e]
                                                       (js/console.log e)
                                                       (ocall! e :stopPropagation)
                                                       (swap! state-atom update :selected-robot
                                                              #(if (= % idx) nil idx))))
                                 (pixi/set-position! [(* 50 (dec idx)) -50])
                                 (pixi/add-to! field)
                                 (add-robot-label! (inc idx)))))
                           (range 3)))]
        (doto field
          (oset! :interactive true)
          (ocall! :on "click" (fn [e] 
                                (ocall! e :stopPropagation)
                                (swap! state-atom assoc :selected-robot nil)))
          (ocall! :on "mousemove"
                  (fn [e] (let [position (ocall! e :data.getLocalPosition field)
                                [px py] [(oget position :x) (oget position :y)]
                                [wx wy] (pixel->world [px py])]
                            (swap! state-atom assoc :cursor {:pixel [px py]
                                                             :world [wx wy]})))))
        (reset! pixi
                {:app app
                 :html html
                 :field field
                 :robots robots
                 :ball ball})
        (.addEventListener js/window "resize" resize-field)
        (resize-field))))



(defn update-ui [old-state new-state]
  (go 
    (when (not= (-> old-state :selected-robot)
                (-> new-state :selected-robot))
      (let [selected-robot (-> new-state :selected-robot)
            button-ids ["r0-button" "r1-button" "r2-button"]]
        (doseq [btn (js/document.querySelectorAll ".r-button")]
          (ocall! btn :classList.remove "active"))
        (when-let [id (get button-ids selected-robot)]
          (ocall! (js/document.getElementById id) :classList.add "active"))))
    (let [{:keys [ball robots]} @pixi]
        (when-let [[wx wy] (-> new-state :cursor :world)]
          (oset! (js/document.getElementById "mouse-pos")
                 :innerText (u/format "[%1 %2]"
                                      (.toFixed wx 3)
                                      (.toFixed wy 3))))
        (when-let [snapshot (-> new-state :latest-snapshot)]
          ;(js/console.log (clj->js snapshot))
          (when-let [{:keys [x y stale-time]} (snapshot :ball)]
            (doto ball
              (oset! :tint (if (< stale-time 0.1)
                             0x00ff00
                             0xaaaaaa))
              (pixi/set-position! (world->pixel [x y]))))
          (doseq [[idx {:keys [x y a]}] (map-indexed vector (snapshot :robots))]
            (doto (nth robots idx)
              (pixi/set-position! (world->pixel [x y]))
              (pixi/set-rotation! (* -1 a))))))))

(defn start-update-loop [state-atom]
  (reset! updates (a/chan (a/sliding-buffer 1)))
  (add-watch state-atom ::updates 
             (fn [_ _ old new]
               (a/put! @updates [old new])))
  (go (loop []
        (when-some [[old new] (<! @updates)]
          (<! (update-ui old new))
          ;(<! (a/timeout 16))
          (recur)))))

(defn stop-update-loop []
  (when-let [upd @updates]
    (a/close! upd)))

(defn initialize! [state-atom]
  (doto state-atom
    (initialize-main-ui!)
    (initialize-pixi!)
    (start-update-loop)))

(defn terminate! []
  (stop-update-loop)
  (try
    (let [[old _] (reset-vals! pixi nil)]
      (when-let [app (:app old)]
        (ocall! app :destroy true true)))
    (catch :default err
      (print "ERROR" err))))

(comment


  (def state-atom rsvisualizer.main/state)
  (-> @state-atom :selected-robot)
  (swap! state-atom assoc :selected-robot nil)
  (def app (-> @pixi :app))
  (def field (-> @pixi :field))
  (def robots (-> @pixi :robots))
  (def ball (-> @pixi :ball))
  
  (doto (first robots)
    (pixi/set-position! [-200 0])
    (pixi/set-rotation! 0))
  
  (js/console.log ball)
  )
  