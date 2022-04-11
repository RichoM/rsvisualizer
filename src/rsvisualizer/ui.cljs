(ns rsvisualizer.ui
  (:require [clojure.core.async :as a :refer [go <!]]
            [oops.core :refer [oget oset! ocall!]]
            [crate.core :as crate]
            [utils.bootstrap :as b]
            [rsvisualizer.pixi :as pui]))

(defonce updates (atom nil))

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
       [:input#ball-prediction.form-check-input {:type "checkbox" :role "switch" :checked false}]
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
         [:button.btn.btn-sm.btn-outline-dark.rounded-pill {:type "button" :disabled true}
          [:i.fa-solid.fa-pen.me-2]
          "Draw"]]]
       [:div.col-3
        [:div.d-grid
         [:button.btn.btn-sm.btn-outline-dark.rounded-pill {:type "button" :disabled true}
          [:i.fa-solid.fa-ruler.me-2]
          "Measure"]]]
       [:div.col-3
        [:div.d-grid
         [:button.btn.btn-sm.btn-outline-dark.rounded-pill {:type "button" :disabled true}
          [:i.fa-solid.fa-arrows-up-down-left-right.me-2]
          "Move"]]]
       [:div.col]]
      [:div#field-panel]]]]))

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
                                    (oget use-degrees :checked))))
  (let [ball-prediction (js/document.getElementById "ball-prediction")]
    (b/on-click ball-prediction #(swap! state-atom assoc-in [:settings :ball-prediction?]
                                        (oget ball-prediction :checked)))))
    
(defn update-selected-robot! [{:keys [selected-robot]}]
  (dotimes [idx 3]
    (let [selected? (= idx selected-robot)
          btn (js/document.getElementById (str "r" idx "-button"))
          row (js/document.getElementById (str "r" idx "-display"))]
      (if selected?
        (do (ocall! btn :classList.add "active")
            (ocall! row :classList.add "text-primary"))
        (do (ocall! btn :classList.remove "active")
            (ocall! row :classList.remove "text-primary"))))))

(defn update-table-display! [new-state]
  (when-let [{:keys [time robot] :as snapshot} (-> new-state :strategy :snapshot)]
    (when (or (nil? (-> new-state :selected-robot))
              (= robot (-> new-state :selected-robot)))
      (oset! (js/document.getElementById "time-display") :innerText (.toFixed time 3))
      (when-let [{:keys [x y]} (snapshot :ball)]
        (oset! (js/document.getElementById "ball-x") :innerText (.toFixed x 3))
        (oset! (js/document.getElementById "ball-y") :innerText (.toFixed y 3)))
      (doseq [[idx {:keys [x y a]}] (map-indexed vector (snapshot :robots))]
        (oset! (js/document.getElementById (str "r" idx "-x")) :innerText (.toFixed x 3))
        (oset! (js/document.getElementById (str "r" idx "-y")) :innerText (.toFixed y 3))
        (oset! (js/document.getElementById (str "r" idx "-a"))
               :innerText (if (-> new-state :settings :degrees?)
                            (str (.toFixed (/ a (/ Math/PI 180)) 3) "deg")
                            (str (.toFixed a 3) "rad")))))))

(defn update-settings-panel! [{{:keys [degrees? ball-prediction?]} :settings}]
  (oset! (js/document.getElementById "use-degrees")
         :checked degrees?)
  (oset! (js/document.getElementById "ball-prediction")
         :checked ball-prediction?))

(defn start-update-loop! [state-atom]
  (reset! updates (a/chan (a/sliding-buffer 1)))
  (add-watch state-atom ::updates 
             (fn [_ _ old new]
               (when (not= (-> old :selected-robot)
                           (-> new :selected-robot))
                 (update-selected-robot! new))
               (when (not= (-> old :settings)
                           (-> new :settings))
                 (update-settings-panel! new))
               (a/put! @updates new)))
  (go (loop []
        (when-some [new-state (<! @updates)]
          (js/console.log (clj->js new-state))
          (update-table-display! new-state)
          (pui/update-snapshot! new-state)
          (<! (a/timeout 16))
          (recur)))))

(defn stop-update-loop! []
  (when-let [upd @updates]
    (a/close! upd)))

(defn initialize-settings! [state-atom]
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
    (pui/initialize!)
    (start-update-loop!)
    (initialize-settings!)))

(defn terminate! []
  (stop-update-loop!)
  (pui/terminate!))

  