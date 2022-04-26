(ns rsvisualizer.ui
  (:require [clojure.core.async :as a :refer [go <!]]
            [oops.core :refer [oget oset! ocall!]]
            [crate.core :as crate]
            [utils.bootstrap :as b]
            [rsvisualizer.pixi :as pui]
            [rsvisualizer.history :as h]))

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
      [:div.form-check.form-switch.text-center.mx-3
       [:input#ghost-robots.form-check-input {:type "checkbox" :role "switch" :checked false}]
       [:label.form-check-.ebal {:for "ghost-robots"} "Ghost robots?"]]
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
      [:div#field-panel
       [:div#canvas-panel]
       [:div#bottom-bar.row.text-center.py-1
        [:div.col]
        [:div.col-auto
         [:button#snapshot-print.btn.btn-sm.btn-outline-dark [:i.fa-solid.fa-terminal]]
         [:span.mx-1]
         [:button#snapshot-copy.btn.btn-sm.btn-outline-dark [:i.fa-regular.fa-copy]]]
        [:div.col-6
           [:input#snapshot-range.form-range {:type "range" :min 0 :max 0 :step 1}]]
        [:div.col-auto
         [:div.btn-group.btn-group-sm {:role "group"}
          [:button#snapshot-previous.btn.btn-outline-dark [:i.fa-solid.fa-backward-step]]
          [:button#snapshot-play.btn.btn-outline-dark [:i.fa-solid.fa-play]]
          [:button#snapshot-pause.btn.btn-outline-dark [:i.fa-solid.fa-pause]]
          [:button#snapshot-next.btn.btn-outline-dark [:i.fa-solid.fa-forward-step]]]]
        [:div.col]]]]]]))

(defn modal-container []
  (crate/html [:div#modal-dialogs]))

(defn toast-container []
  (crate/html [:div#toast-container.toast-container.position-absolute.end-0.top-0.p-3]))

(defn get-element-by-id [id]
  (js/document.getElementById id))

(defn get-selected-strategy [{:keys [selected-snapshot history strategy]}]
  (if selected-snapshot
    (h/get history selected-snapshot)
    strategy))

(defn initialize-main-ui! [state-atom]
  (doto js/document.body
    (oset! :innerHTML "")
    (.appendChild (main-container))
    (.appendChild (modal-container))
    (.appendChild (toast-container)))
  (let [toggle-selection (fn [new] (fn [old] (if (= old new) nil new)))]
    (doseq [[i id] (map-indexed vector ["r0-button" "r1-button" "r2-button"])]
      (b/on-click (get-element-by-id id)
                  #(swap! state-atom update :selected-robot (toggle-selection i)))))
  (let [use-degrees (get-element-by-id "use-degrees")]
    (b/on-click use-degrees #(swap! state-atom assoc-in [:settings :degrees?]
                                    (oget use-degrees :checked))))
  (let [ball-prediction (get-element-by-id "ball-prediction")]
    (b/on-click ball-prediction #(swap! state-atom assoc-in [:settings :ball-prediction?]
                                        (oget ball-prediction :checked))))
  (let [ghost-robots (get-element-by-id "ghost-robots")]
    (b/on-click ghost-robots #(swap! state-atom assoc-in [:settings :ghost-robots?]
                                     (oget ghost-robots :checked))))
  (let [snapshot-previous (get-element-by-id "snapshot-previous")]
    (b/on-click snapshot-previous #(swap! state-atom update :selected-snapshot
                                          (fn [n] (dec (or n (h/count (:history @state-atom))))))))
  (let [snapshot-next (get-element-by-id "snapshot-next")]
    (b/on-click snapshot-next #(swap! state-atom update :selected-snapshot inc)))
  (let [snapshot-play (get-element-by-id "snapshot-play")]
    (oset! snapshot-play :hidden true)
    (b/on-click snapshot-play #(swap! state-atom assoc :selected-snapshot nil)))
  (let [snapshot-pause (get-element-by-id "snapshot-pause")]
    (b/on-click snapshot-pause #(swap! state-atom assoc :selected-snapshot
                                       (dec (h/count (:history @state-atom))))))
  (let [snapshot-range (get-element-by-id "snapshot-range")]
    (b/on-input snapshot-range #(swap! state-atom assoc :selected-snapshot
                                       (int (oget snapshot-range :value)))))
  (let [snapshot-print (get-element-by-id "snapshot-print")]
    (b/on-click snapshot-print
                #(do
                   (b/show-toast-msg "Current snapshot printed to the console"
                                     [:i.fa-solid.fa-terminal])
                   (js/console.log (clj->js (get-selected-strategy @state-atom))))))
  (let [snapshot-copy (get-element-by-id "snapshot-copy")]
    (b/on-click snapshot-copy
                #(let [str (pr-str (get-selected-strategy @state-atom))]
                   (b/show-modal (b/make-modal :header (list b/close-modal-btn)
                                               :body [:div.font-monospace {:style "user-select: all;"} str]))))))

(defn update-selected-robot! [{:keys [selected-robot]}]
  (dotimes [idx 3]
    (let [selected? (= idx selected-robot)
          btn (get-element-by-id (str "r" idx "-button"))
          row (get-element-by-id (str "r" idx "-display"))]
      (if selected?
        (do (ocall! btn :classList.add "active")
            (ocall! row :classList.add "text-primary"))
        (do (ocall! btn :classList.remove "active")
            (ocall! row :classList.remove "text-primary"))))))

(defn update-selected-snapshot! [{:keys [selected-snapshot history]}]
  (let [max (dec (h/count history))
        disabled? (h/empty? history)]
    (doto (get-element-by-id "snapshot-print")
      (oset! :disabled disabled?))
    (doto (get-element-by-id "snapshot-copy")
      (oset! :disabled disabled?))
    (doto (get-element-by-id "snapshot-range")
      (oset! :disabled disabled?)
      (oset! :max max)
      (oset! :value (or selected-snapshot max)))
    (doto (get-element-by-id "snapshot-play")
      (oset! :disabled disabled?)
      (oset! :hidden (nil? selected-snapshot)))
    (doto (get-element-by-id "snapshot-pause")
      (oset! :disabled disabled?)
      (oset! :hidden (some? selected-snapshot)))
    (doto (get-element-by-id "snapshot-previous")
      (oset! :disabled (or disabled?
                           (and (some? selected-snapshot)
                                (<= selected-snapshot 0)))))
    (doto (get-element-by-id "snapshot-next")
      (oset! :disabled (or disabled?
                           (nil? selected-snapshot)
                           (>= selected-snapshot max))))))

(defn to-fixed [n d]
  (if n (.toFixed n d) ""))

(defn update-table-display! [new-state]
  (if-let [{:keys [time robot] :as snapshot} (-> new-state get-selected-strategy :snapshot)]
    (when (or (nil? (-> new-state :selected-robot))
              (= robot (-> new-state :selected-robot)))
      (oset! (get-element-by-id "time-display") :innerText (to-fixed time 3))
      (when-let [{:keys [x y]} (snapshot :ball)]
        (oset! (get-element-by-id "ball-x") :innerText (to-fixed x 3))
        (oset! (get-element-by-id "ball-y") :innerText (to-fixed y 3)))
      (doseq [[idx robot-data] (map-indexed vector (snapshot :robots))]
        (when-let [{:keys [x y a]} robot-data]
          (oset! (get-element-by-id (str "r" idx "-x")) :innerText (to-fixed x 3))
          (oset! (get-element-by-id (str "r" idx "-y")) :innerText (to-fixed y 3))
          (oset! (get-element-by-id (str "r" idx "-a"))
                 :innerText (if (-> new-state :settings :degrees?)
                              (str (to-fixed (/ a (/ Math/PI 180)) 3) "deg")
                              (str (to-fixed a 3) "rad"))))))
    (doseq [element-id ["time-display" "ball-x" "ball-y"
                        "r0-x" "r0-y" "r0-a"
                        "r1-x" "r1-y" "r1-a"
                        "r2-x" "r2-y" "r2-a"]]
      (oset! (get-element-by-id element-id) :innerText "?"))))

(defn update-settings-panel!
  [{{:keys [degrees? ball-prediction? ghost-robots?]} :settings}]
  (oset! (get-element-by-id "use-degrees")
         :checked degrees?)
  (oset! (get-element-by-id "ball-prediction")
         :checked ball-prediction?)
  (oset! (get-element-by-id "ghost-robots")
         :checked ghost-robots?))

(defn start-update-loop! [state-atom]
  (let [updates* (reset! updates (a/chan (a/sliding-buffer 1)))]
    (add-watch state-atom ::updates
               (fn [_ _ old new]
                 (when (not= (-> old :selected-robot)
                             (-> new :selected-robot))
                   (update-selected-robot! new))
                 (when (not= (-> old :settings)
                             (-> new :settings))
                   (update-settings-panel! new))
                 (a/put! updates* new)))
    (go (loop []
          (when-some [new-state (<! updates*)]
            (let [timeout (a/timeout 32)]
              (update-table-display! new-state)
              (update-selected-snapshot! new-state)
              (pui/update-snapshot! new-state)
              (<! timeout)
              (recur)))))))

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

  