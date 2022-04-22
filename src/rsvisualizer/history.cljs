(ns rsvisualizer.history
  (:refer-clojure :exclude [get count empty?])
  (:require [cljs.core :as clj]))

(defn append [history data & [clear?]]
  (let [string (js/LZString.compress data)]
    (if (and history (not clear?))
      (update history :values conj string)
      {:values [string]})))

(defn get [history i]
  (when-let [{:keys [values]} history]
    (let [last (- (clj/count values) 1)]
      (when (<= 0 i last)
        (js->clj (js/JSON.parse (js/LZString.decompress 
                                 (nth values i)))
                 :keywordize-keys true)))))

(defn count [history]
  (-> history :values clj/count))