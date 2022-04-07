(ns rsvisualizer.main
  (:require [clojure.core.async :as a :refer [go <!]]
            [clojure.string :as str]
            [oops.core :refer [oget oset! ocall!]]
            [utils.bootstrap :as b]
            [utils.core :as u]
            [rsvisualizer.ui :as ui]))

(enable-console-print!)

(defonce state (atom {}))
(defonce ws (atom nil))

(defn show-msg [msg & [icon]]
  (-> (b/make-toast :header (list (when icon icon)
                                  [:strong.me-auto msg]
                                  [:button.btn-close {:type "button" :data-bs-dismiss "toast" :aria-label "Close"}]))
      (b/show-toast))
  (print msg))

(declare connect-to-server!)

(defn ws-url [address]
  (str "ws://" address "/updates"))

(defn ask-address [default]
  (go (str/trim (or (<! (b/prompt "Enter address:" "" default)) ""))))

(defn show-connection-dialog [default]
  (go (let [address (<! (ask-address default))]
        (if (empty? address)
          (show-msg "Staying disconnected. Reload page to retry...")
          (<! (connect-to-server! address))))))

(defn disconnect-from-server! []
  (go (when-let [[socket _] (reset-vals! ws nil)]
        (.close socket))))

(defn connect-ws! [url]
  (let [result (a/promise-chan)
        socket (js/WebSocket. url)]
    (doto socket
      (oset! :onerror #(a/close! result))
      (oset! :onopen #(a/put! result socket)))
    result))

(defn try-to-connect-ws!
  [address]
  (go (show-msg (str "Connecting to " address))
      (if-let [socket (<! (connect-ws! (ws-url address)))]
        (do (show-msg "Connection successful!" [:i.fa-solid.fa-circle-check.me-3])
            (oset! js/localStorage "!rsvisualizer-address" address)
            (reset! ws socket)
            (doto socket
              (oset! :onclose 
                     #(go (loop [retry 1]
                            (if (> retry 10)
                              (do (show-msg "Too many retries. Giving up...")
                                  (<! (a/timeout 1000))
                                  (<! (show-connection-dialog address)))
                              (if-not (= socket @ws)
                                (print "OLD SOCKET. BYE")
                                (when-not (<! (try-to-connect-ws! address))
                                  (show-msg (u/format "Retrying in 1s (retry: %1)" retry))
                                  (<! (a/timeout 1000))
                                  (recur (inc retry))))))))
              (oset! :onmessage
                     (fn [msg] (swap! state assoc :strategy
                                      (js->clj (js/JSON.parse (oget msg :data))
                                               :keywordize-keys true))))))
        (do (show-msg "Connection failed" [:i.fa-solid.fa-triangle-exclamation.me-3])
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
