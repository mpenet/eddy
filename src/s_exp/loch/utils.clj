(ns s-exp.loch.utils
  (:import
   (java.util Iterator Enumeration)
   (clojure.lang IReduceInit)))

(defn string-builder
  ([] (StringBuilder.))
  ([^StringBuilder sb x] (.append sb x))
  ([^StringBuilder sb] (.toString sb)))

(defn iterator-reducible
  [^Iterator iter]
  (reify IReduceInit
    (reduce [_ f init]
      (loop [^Iterator it iter
             state init]
        (if (reduced? state)
          @state
          (if (.hasNext it)
            (recur it (f state (.next it)))
            state))))))

(defn enum-reducible
  [^Enumeration e]
  (reify IReduceInit
    (reduce [_ f init]
      (loop [^Enumeration e e
             state init]
        (if (reduced? state)
          @state
          (if (.hasMoreElements e)
            (recur e (f state (.nextElement e)))
            state))))))
