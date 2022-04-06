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
   [:div#main-container.container-fluid
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
       [:input#ball-prediction.form-check-input {:type "checkbox" :role "switch" :checked false :disabled true}]
       [:label.form-check-.ebal {:for "ball-prediction"} "Ball prediction?"]]
      [:div.form-check.form-switch.text-center.mx-3
       [:input#use-degrees.form-check-input {:type "checkbox" :role "switch" :checked false}]
       [:label.form-check-.ebal {:for "use-degrees"} "Use degrees?"]]
      [:div#table-display.position-absolute.bottom-0
       [:div.col
        [:div.row
         [:div.col-2.text-start "Time:"]
         [:div#time-display.col-3.text-end "?"]]
        [:div#ball-display.row
         [:div.col-2.text-start "Ball:"]
         [:div#ball-x.col-3.text-end "?"]
         [:div#ball-y.col-3.text-end "?"]]
        [:div#r0-display.row
         [:div.col-2.text-start "R1:"]
         [:div#r0-x.col-3.text-end "?"]
         [:div#r0-y.col-3.text-end "?"]
         [:div#r0-a.col-4.text-end "?"]]
        [:div#r1-display.row
         [:div.col-2.text-start "R2:"]
         [:div#r1-x.col-3.text-end "?"]
         [:div#r1-y.col-3.text-end "?"]
         [:div#r1-a.col-4.text-end "?"]]
        [:div#r2-display.row
         [:div.col-2.text-start "R3:"]
         [:div#r2-x.col-3.text-end "?"]
         [:div#r2-y.col-3.text-end "?"]
         [:div#r2-a.col-4.text-end "?"]]]]]
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
                  #(swap! state-atom update :selected-robot (toggle-selection idx)))))
  (let [use-degrees (js/document.getElementById "use-degrees")]
    (b/on-click use-degrees #(swap! state-atom assoc-in [:settings :degrees?]
                                    (oget use-degrees :checked)))))
    
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
                         label-style (js/PIXI.TextStyle. (clj->js {:fontFamily "sans-serif"
                                                                   :fontSize 26
                                                                   :fontWeight "bold"
                                                                   :fill "#ffffff"
                                                                   :stroke "#000000"
                                                                   :strokeThickness 4}))]
                     (mapv (fn [idx]
                             (let [add-robot-label! (fn [robot idx]
                                                      (let [label (pixi/make-label! idx label-style)
                                                            ticker #(pixi/set-position! label [(oget robot :x)
                                                                                               (+ (oget robot :y) 0)])]
                                                        (pixi/add-child! field label)
                                                        (pixi/add-ticker! app ticker)))]
                               (doto (pixi/make-sprite! robot-texture)
                                 (oset! :tint 0x00aaff)
                                 (oset! :interactive true)
                                 (ocall! :on "click" (fn [e]
                                                       (ocall! e :stopPropagation)
                                                       (swap! state-atom update :selected-robot
                                                              #(if (= % idx) nil idx))))
                                 (pixi/set-position! [(* 50 (dec idx)) -50])
                                 (pixi/add-to! field)
                                 (add-robot-label! (inc idx)))))
                           (range 3)))
            cursor (let [label-style (js/PIXI.TextStyle. (clj->js {:fontFamily "monospace"
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
                     label)]
        (doto field
          (oset! :interactive true)
          (ocall! :on "click" (fn [e]
                                (ocall! e :stopPropagation)
                                (swap! state-atom assoc :selected-robot nil)))
          (ocall! :on "mousemove"
                  (fn [e] (let [position (ocall! e :data.getLocalPosition field)
                                pixel-coords [(oget position :x) (oget position :y)]
                                world-coords (pixel->world pixel-coords)]
                            (swap! state-atom assoc :cursor {:pixel pixel-coords
                                                             :world world-coords})))))
        (reset! pixi
                {:app app
                 :html html
                 :field field
                 :robots robots
                 :ball ball
                 :cursor cursor})
        (.addEventListener js/window "resize" resize-field)
        (resize-field))))

(defn update-ui [old-state new-state]
  (go
    (when-let [{:keys [ball robots]} @pixi]
      (when (not= (-> old-state :selected-robot)
                  (-> new-state :selected-robot))
        (let [selected-robot (-> new-state :selected-robot)]
          (doseq [[idx robot] (map-indexed vector robots)]
            (let [selected? (= idx selected-robot)
                  btn (js/document.getElementById (str "r" idx "-button"))
                  row (js/document.getElementById (str "r" idx "-display"))]
              (if selected?
                (do (ocall! btn :classList.add "active")
                    (ocall! row :classList.add "text-primary")
                    (oset! robot :tint 0x99ffff))
                (do (ocall! btn :classList.remove "active")
                    (ocall! row :classList.remove "text-primary")
                    (oset! robot :tint 0x00aaff)))))))
      (when-let [{:keys [time] :as snapshot} (-> new-state :latest-snapshot)]
        (oset! (js/document.getElementById "time-display") :innerText (.toFixed time 3))
        (when-let [{:keys [x y stale-time]} (snapshot :ball)]
          (oset! (js/document.getElementById "ball-x") :innerText (.toFixed x 3))
          (oset! (js/document.getElementById "ball-y") :innerText (.toFixed y 3))
          (doto ball
            (oset! :tint (if (< stale-time 0.1) 0x00ff00 0xaaaaaa))
            (pixi/set-position! (world->pixel [x y]))))
        (doseq [[idx {:keys [x y a]}] (map-indexed vector (snapshot :robots))]
          (oset! (js/document.getElementById (str "r" idx "-x")) :innerText (.toFixed x 3))
          (oset! (js/document.getElementById (str "r" idx "-y")) :innerText (.toFixed y 3))
          (oset! (js/document.getElementById (str "r" idx "-a"))
                 :innerText (if (-> new-state :settings :degrees?)
                              (str (.toFixed (/ a (/ Math/PI 180)) 3) "deg")
                              (str (.toFixed a 3) "rad")))
          (doto (nth robots idx)
            (pixi/set-position! (world->pixel [x y]))
            (pixi/set-rotation! (* -1 a))))))
    (when (not= (-> old-state :settings)
                (-> new-state :settings))
      (oset! (js/document.getElementById "use-degrees") :checked
             (-> new-state :settings :degrees?)))))

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

(defn initialize-settings [state-atom]
  (swap! state-atom assoc :settings
         (js->clj (js/JSON.parse (oget js/localStorage "?rsvisualizer-settings"))
                  :keywordize-keys true))
  (add-watch state-atom ::settings
             (fn [_ _ {old :settings} {new :settings}]
               (when (not= old new)
                 (oset! js/localStorage "!rsvisualizer-settings"
                        (js/JSON.stringify (clj->js new)))))))

(defn initialize! [state-atom]
  (doto state-atom
    (initialize-main-ui!)
    (initialize-pixi!)
    (start-update-loop)
    (initialize-settings)))

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
  (def cursor (-> @pixi :cursor))
  
  (oget cursor :height)
  ((oget cursor :parent.x))
  (js-keys cursor)
  (doto (first robots)
    (pixi/set-position! [-200 0])
    (pixi/set-rotation! 0))

  (js/console.log ball)
  )
  