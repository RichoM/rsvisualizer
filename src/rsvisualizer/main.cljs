(ns rsvisualizer.main
  (:require [clojure.core.async :as a :refer [go <!]]
            [rsvisualizer.ui :as ui]))

(enable-console-print!)

(defonce state (atom {}))

(defn init []
  (go (print "RICHO!")
      (<! (ui/initialize! state))))


(defn ^:dev/before-load-async reload-begin* [done]
  (go (<! (ui/terminate! state))
      (done)))

(defn ^:dev/after-load-async reload-end* [done]
  (go (<! (ui/initialize! state))
      (done)))