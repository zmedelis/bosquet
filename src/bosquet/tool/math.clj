(ns bosquet.tool.math 
  (:require
   [taoensso.timbre :as timbre]))


(defn ^{:desc "add 'x' and 'y'"} add
    [^{:type "number" :desc "First number to add"} x
     ^{:type "number" :desc "Second number to add"} y]
    (timbre/infof "Applying add for %s %s" x y)
    (+ (if (number? x)  x (Float/parseFloat x) )
       (if (number? y)  y (Float/parseFloat y) )))


(defn ^{:desc "subtract 'y' from 'x'"} sub
    [^{:type "number" :desc "Number to subtract from"} x
     ^{:type "number" :desc "Number to subtract"} y]
  (timbre/infof "Applying sub for %s %s" x y)
  (- (if (number? x)  x (Float/parseFloat x) )
       (if (number? y)  y (Float/parseFloat y) )))
