(ns rsvisualizer.main
  (:require [clojure.core.async :as a :refer [go <!]]
            [oops.core :refer [oget oset! ocall!]]
            [rsvisualizer.ui :as ui]))

(enable-console-print!)

(defonce state (atom {}))

(defn connect-to-ws [url]
  (let [result (a/promise-chan)
        socket (js/WebSocket. url)]
    (doto socket
      (oset! :onerror (fn [err] (a/put! result err)))
      (oset! :onopen (fn [] (a/put! result socket)))
      (oset! :onmessage
             (fn [msg] (swap! state assoc :latest-snapshot
                              (js->clj (js/JSON.parse (oget msg :data))
                                       :keywordize-keys true)))))
    result))

(defn connect-to-server []
  (go (let [url "ws://192.168.0.23:7777/updates"]
        (<! (connect-to-ws url)))))

(defn init []
  (go (print "RICHO!")
      (<! (connect-to-server))
      (<! (ui/initialize! state))))


(defn ^:dev/before-load-async reload-begin* [done]
  (go (<! (ui/terminate!))
      (done)))

(defn ^:dev/after-load-async reload-end* [done]
  (go (<! (ui/initialize! state))
      (<! (connect-to-server))
      (done)))