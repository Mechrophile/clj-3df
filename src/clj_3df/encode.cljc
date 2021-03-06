(ns clj-3df.encode
  "Utilties for encoding query plans to something that the backend can
  understand. In particular, attributes and symbols will be encoded as
  integers.")

(def nextID (atom 0))

(def encode-symbol (memoize (fn [sym] #?(:clj  (clojure.lang.RT/nextID)
                                         :cljs (swap! nextID inc)))))

(defn encode-keyword [kw]
  (subs (str kw) 1))

(defn encode-plan [plan]
  (cond
    (symbol? plan)      (encode-symbol plan)
    (keyword? plan)     (encode-keyword plan)
    (sequential? plan)  (mapv encode-plan plan)
    (associative? plan) (reduce-kv (fn [m k v] (assoc m k (encode-plan v))) {} plan)
    (nil? plan)         (throw (ex-info "Plan contain's nils."
                                        {:causes #{:contains-nil}}))
    :else               plan))

(defn encode-rule [rule]
  (let [{:keys [name plan]} rule]
    {:name name
     :plan (encode-plan plan)}))

(defn encode-rules [rules]
  (mapv encode-rule rules))

(comment

  (encode-keyword :name)
  (encode-keyword :person/name)
  
  (encode-plan [])
  (encode-plan '?name)
  (encode-plan '{:MatchA [?e :name ?n]})
  (encode-plan '{:Join [?n {:MatchA [?e1 :name ?n]} {:MatchA [?e2 :name ?n]}]})
  (encode-plan '{:Join [?n {:MatchA [?e1 :name ?n]} {:MatchA [?e2 :name ?n]}]})
  (encode-plan '{:Project
                 [[?e1 ?n ?e2] {:Join [?n {:MatchA [?e1 :name ?n]} {:MatchA [?e2 :name ?n]}]}]})
  )
