(ns utils.pixi
  (:require [clojure.core.async :as a :refer [go <!]]
            [oops.core :refer [oget oget+ oset! ocall!]]))

(defn make-application! [html]
  (let [app (js/PIXI.Application. (clj->js {:resizeTo html :transparent true
                                            ;:resolution (or js/window.devicePixelRatio 1)
                                            :antialias true
                                            }))]
    (ocall! html :appendChild (oget app :view))
    app))

(defn load-texture! [path]
  (let [result (a/promise-chan)]
    (doto (js/PIXI.Loader.)
      (ocall! :add path)
      (ocall! :load (fn [_ resources]
                      (let [texture (oget (aget resources path) :texture)]
                        (a/put! result texture)))))
    result))

(defn make-sprite! [texture]
  (doto (js/PIXI.Sprite. texture)
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

(defn set-rotation! [obj rot]
  (oset! obj :rotation rot))

(defn set-height! [obj h]
  (let [w (* (oget obj :width)
             (/ h (oget obj :height)))]
    (doto obj
      (oset! :height h)
      (oset! :width w))))
