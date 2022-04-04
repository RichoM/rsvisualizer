(ns utils.pixi
  (:require [oops.core :refer [oget oset! ocall!]]))

(defn make-application! [html]
  (let [app (js/PIXI.Application. (clj->js {:resizeTo html :transparent true}))]
    (ocall! html :appendChild (oget app :view))
    app))

(defn make-sprite! [path]
  (doto (js/PIXI.Sprite.from path)
    (oset! :anchor.x 0.5)
    (oset! :anchor.y 0.5)))

(defn add-child! [parent child]
  (ocall! parent :addChild child))

(defn add-to! [child parent]
  (ocall! parent :addChild child))

(defn get-center [obj]
  [(/ (oget obj :width) 2)
   (/ (oget obj :height) 2)])

(defn get-screen-center [app]
  (get-center (oget app :screen)))

(defn set-position! [obj [x y]]
  (doto obj
    (oset! :x x)
    (oset! :y y)))

(defn set-height! [obj h]
  (let [w (* (oget obj :width)
             (/ h (oget obj :height)))]
    (doto obj
      (oset! :height h)
      (oset! :width w))))