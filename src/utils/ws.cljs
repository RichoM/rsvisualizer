(ns utils.ws
  (:require [clojure.core.async :as a :refer [go <!]]
            [oops.core :refer [oget oset! ocall!]]
            [clojure.string :as str]))

(defn ws-url [address]
  (str "ws://" address))

(defn wss-url [address]
  (str "wss://" address))

(defn connect! [url]
  (let [result (a/promise-chan)
        socket (js/WebSocket. url)]
    (doto socket
      (oset! :onerror #(a/close! result))
      (oset! :onopen #(a/put! result socket)))
    result))

(defn try-connect! [address]
  (go (if (or (str/starts-with? address "ws://")
              (str/starts-with? address "wss://"))
        (<! (connect! address))
        (loop [[url & urls] [(wss-url address)
                             (ws-url address)]]
          (when url
            (if-let [socket (<! (connect! url))]
              socket
              (recur urls)))))))