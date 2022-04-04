(ns rsvisualizer.ui
  (:require [clojure.core.async :as a :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [oops.core :refer [oget oset! ocall!]]
            [crate.core :as crate]
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
    
(defn resize-field [app html]
  (doto html
    (oset! :style.height
           (u/format "calc(100% - %1px)"
                     (+ 30 (oget (js/document.querySelector "#top-bar")
                                 :offsetHeight))))
    (oset! :style.width
           (u/format "calc(100% - %1px)"
                     (+ 30 (oget (js/document.querySelector "#side-bar")
                                 :offsetWidth)))))
  (ocall! app :resize))

(defn initialize-pixi! []
  (let [html (js/document.getElementById "field-panel")
        app (js/PIXI.Application. (clj->js {:resizeTo html
                                            :transparent true}))]
    (swap! state assoc-in [:pixi :app] app)
    (ocall! html :appendChild (oget app :view))
    (.addEventListener js/window :resize #(resize-field app html))
    (resize-field app html)
    (let [field-sprite (js/PIXI.Sprite.from "imgs/field.png")]
      (ocall! (oget app :stage) :addChild field-sprite)
      (doto field-sprite
        (oset! :anchor.x 0.5)
        (oset! :anchor.y 0.5)
        (oset! :x (/ (oget app :screen.width) 2))
        (oset! :y (/ (oget app :screen.height) 2))))))

(defn initialize! []
  (go (initialize-main-ui!)
      (initialize-pixi!)))

(defn terminate! []
  (go (ocall! (-> @state :pixi :app)
              :destroy true {:children true
                             :texture true
                             :baseTexture true})))

(comment
  (def app (-> @state :pixi :app))

  (js/console.log app)


  )