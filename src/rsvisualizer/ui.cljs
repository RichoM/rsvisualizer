(ns rsvisualizer.ui
  (:require [clojure.core.async :as a :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [oops.core :refer [oget oset! ocall!]]
            [crate.core :as crate]
            [utils.pixi :as pixi]
            [utils.core :as u]))

(defonce state (atom {}))

(defn main-container []
  (crate/html
   [:div.container-fluid
    [:div.row
     [:div#side-bar.col-auto
      [:h3.my-1.text-center "RS Visualizer"]
      [:div.row.my-1]
      [:div.d-grid
       [:button.btn.btn-sm.btn-outline-dark.rounded-pill
        {:type "button" :data-bs-toggle "button"}
        [:i.fa-solid.fa-cube.me-2]
        "Robot 1"]]
      [:div.row.my-1]
      [:div.d-grid
       [:button.btn.btn-sm.btn-outline-dark.rounded-pill
        {:type "button" :data-bs-toggle "button"}
        [:i.fa-solid.fa-cube.me-2]
        "Robot 2"]]
      [:div.row.my-1]
      [:div.d-grid
       [:button.btn.btn-sm.btn-outline-dark.rounded-pill
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
      [:div#field-panel]]]]
    ))



(defn initialize-main-ui! []
  (doto js/document.body
    (oset! :innerHTML "")
    (.appendChild (main-container))))
    
(defn resize-field [state-atom]
  (let [{:keys [html app field]} (-> @state-atom :pixi)]
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
            robots (let [robot-texture (<! (pixi/load-texture! "imgs/robot.png"))]
                     (vec (repeatedly 3 #(doto (pixi/make-sprite! robot-texture)
                                           (oset! :tint 0x00aaff)
                                           (pixi/set-position! [0 0])
                                           (pixi/add-to! field)))))
            ball (doto (pixi/make-sprite! (<! (pixi/load-texture! "imgs/ball.png")))
                   (oset! :tint 0x00ff00)
                   (pixi/set-position! [0 0])
                   (pixi/add-to! field))]
        (swap! state-atom assoc :pixi
               {:app app
                :html html
                :field field
                :robots robots
                :ball ball})
        (.addEventListener js/window "resize" #(resize-field state-atom))
        (resize-field state-atom))))

(defn initialize! []
  (go (initialize-main-ui!)
      (<! (initialize-pixi! state))))

(defn terminate! []
  (go (ocall! (-> @state :pixi :app)
              :destroy true {:children true
                             :texture true
                             :baseTexture true})))

(comment
  (def app (-> @state :pixi :app))
  (def field (-> @state :pixi :field))
  (def robots (-> @state :pixi :robots))
  (def ball (-> @state :pixi :ball))

  (oset! ball :tint 0x00ff00)

  
  (def r0 (first robots))
  (pixi/set-position! r0 [-400 0])
  (pixi/set-rotation! r0 1.32)
  (oset! r0 :tint )
  )