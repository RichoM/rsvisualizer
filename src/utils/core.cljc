(ns utils.core
  (:refer-clojure :exclude [format])
  (:require [clojure.string :as str]))

(defn seek
  ;; NOTE(Richo): Code taken from Niki Tonsky's tweet
  ;; https://twitter.com/nikitonsky/status/1430538652984221703
  ([pred coll]
   (reduce #(when (pred %2) (reduced %2)) nil coll))
  ([pred coll default-value]
   (or (reduce #(when (pred %2) (reduced %2)) nil coll)
       default-value)))

(defn index-by
  "Returns a map of the elements of `coll` keyed by the result of `f` on each
   element.  The value at each key will be a single element (in contrast to
   `clojure.core/group-by`).  Therefore `f` should generally return an unique
   key for every element - otherwise elements get discarded."
  ;; NOTE(Richo): Taken from the following thread
  ;; https://www.reddit.com/r/Clojure/comments/8iw3b8/plain_groupby_for_unique_values/
  [f coll]
  (persistent! (reduce #(assoc! %1 (f %2) %2) (transient {}) coll)))

(defn index-of ^long
  [^java.util.List v e]
  (.indexOf v e))

(defn format
  "Simple string formatting function. It doesn't support any fancy features
  (but works in cljs)"
  [text & args]
  (loop [t text, i 0]
    (if-let [val (nth args i nil)]
      (recur
       (str/replace t (str "%" (inc i)) (str val))
       (inc i))
      t)))

(defn dist [[x y] [x' y']]
  (let [dx (- x' x)
        dy (- y' y)]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))