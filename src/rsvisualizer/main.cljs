(ns rsvisualizer.main
  (:require [clojure.core.async :as a :refer [go <!]]
            [clojure.string :as str]
            [oops.core :refer [oget oset! ocall!]]
            [utils.bootstrap :as b]
            [utils.core :as u]
            [utils.ws :as ws]
            [rsvisualizer.ui :as ui]
            [rsvisualizer.history :as h]))

(enable-console-print!)

(defonce state (atom {}))
(defonce ws (atom nil))

(declare connect-to-server!)

(defn ask-address [default]
  (go (str/trim (or (<! (b/prompt "Enter address:" "" default)) ""))))

(defn show-connection-dialog [default]
  (go (let [address (<! (ask-address default))]
        (if (empty? address)
          (b/show-toast-msg "Staying disconnected. Reload page to retry...")
          (<! (connect-to-server! address))))))

(defn disconnect-from-server! []
  (go (when-let [[socket _] (reset-vals! ws nil)]
        (.close socket))))

(defn try-to-connect-ws!
  [address]
  (go (b/show-toast-msg (str "Connecting to " address))
      (if-let [socket (<! (ws/try-connect! (str address "/updates")))]
        (do (b/show-toast-msg "Connection successful!" [:i.fa-solid.fa-circle-check])
            (oset! js/localStorage "!rsvisualizer-address" address)
            (reset! ws socket)
            (doto socket
              (oset! :onclose
                     #(go (loop [retry 1]
                            (if (> retry 10)
                              (do (b/show-toast-msg "Too many retries. Giving up...")
                                  (<! (a/timeout 1000))
                                  (<! (show-connection-dialog address)))
                              (if-not (= socket @ws)
                                (print "OLD SOCKET. BYE")
                                (when-not (<! (try-to-connect-ws! address))
                                  (b/show-toast-msg (u/format "Retrying in 1s (retry: %1)" retry))
                                  (<! (a/timeout 1000))
                                  (recur (inc retry))))))))
              (oset! :onmessage
                     (fn [msg] (let [data (oget msg :data)
                                     new-strategy (js->clj (js/JSON.parse data)
                                                           :keywordize-keys true)]
                                 (swap! state
                                        (fn [{previous-strategy :strategy, :as state}]
                                          (let [clear? (< (-> new-strategy :snapshot :time)
                                                          (-> previous-strategy :snapshot :time))]
                                            (-> state
                                                (assoc :strategy new-strategy)
                                                (update :history h/append data clear?))))))))))
        (do (b/show-toast-msg "Connection failed" [:i.fa-solid.fa-triangle-exclamation])
            nil))))

(defn connect-to-server!
  ([] (connect-to-server! (or (oget js/localStorage "?rsvisualizer-address") 
                              "127.0.0.1:7777")))
  ([address]
   (go (when-not (<! (try-to-connect-ws! address))
         (<! (a/timeout 1000))
         (<! (show-connection-dialog address))))))

(defn init []
  (go (print "RICHO!")
      (ui/initialize! state)
      (<! (connect-to-server!))))

(defn ^:dev/before-load-async reload-begin* [done]
  (go (b/hide-modals)
      (ui/terminate!)
      (<! (disconnect-from-server!))
      (done)))

(defn ^:dev/after-load-async reload-end* [done]
  (go (ui/initialize! state)
      (<! (connect-to-server!))
      (done)))
