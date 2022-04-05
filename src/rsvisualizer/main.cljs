(ns rsvisualizer.main
  (:require [clojure.core.async :as a :refer [go <!]]
            [oops.core :refer [oget oset! ocall!]]
            [utils.core :as u]
            [rsvisualizer.ui :as ui]))

(enable-console-print!)

(defonce state (atom {}))
(defonce ws (atom nil))

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
  [url]
  (go (print "Waiting for connection...")
      (if-let [socket (<! (connect-ws! url))]
        (do (print "Connection successful!")
            (reset! ws socket)
            (doto socket
              (oset! :onclose 
                     #(go (loop [retry 1]
                            (if (> retry 10)
                              (print "Too many retries. BYE")
                              (if-not (= socket @ws)
                                (print "OLD SOCKET. BYE")
                                (when-not (<! (try-to-connect-ws! url))
                                  (print (u/format "Retrying in 1s (retry: %1)" retry))
                                  (<! (a/timeout 1000))
                                  (recur (inc retry))))))))
              (oset! :onmessage
                     (fn [msg] (swap! state assoc :latest-snapshot
                                      (js->clj (js/JSON.parse (oget msg :data))
                                               :keywordize-keys true))))))
        (print "Connection failed"))))

(defn connect-to-server! []
  (go (let [url "ws://192.168.0.23:7777/updates"
            ;url "ws://localhost:7777/updates"
            ]
        (<! (try-to-connect-ws! url)))))

(defn init []
  (go (print "RICHO!")
      (ui/initialize! state)
      (<! (connect-to-server!))))

(defn ^:dev/before-load-async reload-begin* [done]
  (go (ui/terminate!)
      (<! (disconnect-from-server!))
      (done)))

(defn ^:dev/after-load-async reload-end* [done]
  (go (ui/initialize! state)
      (<! (connect-to-server!))
      (done)))
