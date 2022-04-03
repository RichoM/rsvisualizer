(ns rsvisualizer.main
  (:require [clojure.core.async :as a :refer [go <!]]
            [rsvisualizer.ui :as ui]))

(enable-console-print!)

(defn init []
  (go (print "RICHO!")
      (<! (ui/initialize!))))


(defn ^:dev/before-load-async reload-begin* [done]
  (go (<! (ui/terminate!))
      (done)))

(defn ^:dev/after-load-async reload-end* [done]
  (go (<! (ui/initialize!))
      (done)))