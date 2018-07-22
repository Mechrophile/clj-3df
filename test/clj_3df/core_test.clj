(ns clj-3df.core-test
  (:require
   [clojure.test :refer [deftest is testing run-tests]]
   [manifold.stream :as stream]
   [manifold.bus :as bus]
   [clj-3df.core :as df :refer [exec! create-conn register-query register-plan transact]]))

(defn- debug-conn []
  (let [conn (create-conn "ws://127.0.0.1:6262")]
    (stream/consume #(println %) (bus/subscribe (:out conn) :out))
    conn))

(deftest test-basic-conjunction
  (let [db (df/create-db {:name {:db/valueType :String} :age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (register-query db "basic-conjunction" '[:find ?e ?age :where [?e :name "Mabel"] [?e :age ?age]])
      (transact db [[:db/add 1 :name "Dipper"] [:db/add 1 :age 26]])
      (transact db [{:db/id 2 :name "Mabel" :age 26}])
      (expect-> out (is (= [[[2 26] 1]] out)))
      (transact db [[:db/retract 2 :name "Mabel"]])
      (expect-> out (is (= [[[2 26] -1]] out))))))

(deftest test-multi-conjunction
  (let [db (df/create-db {:name {:db/valueType :String} :age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (register-query db "multi-conjunction" '[:find ?e1 ?e2
                                               :where
                                               [?e1 :name ?name] [?e1 :age ?age]
                                               [?e2 :name ?name] [?e2 :age ?age]])
      (transact db [{:db/id 1 :name "Dipper" :age 26}
                    {:db/id 2 :name "Mabel" :age 26}
                    {:db/id 3 :name "Soos" :age 32}])
      (expect-> out (is (= [[[1 1] 1] [[2 2] 1] [[3 3] 1]] out)))
      (transact db [[:db/retract 2 :name "Mabel"]])
      (expect-> out (is (= [[[2 2] -1]] out))))))

(deftest test-cartesian
  (let [db (df/create-db {:name {:db/valueType :String}})]
    (exec! (debug-conn)
      ;; (register-query db "cartesian" '[:find ?e1 ?e2 :where [?e1 :name ?n1] [?e2 :name ?n2]])
      (register-plan db "cartesian" '{:Project [{:Join [{:HasAttr [?e1 :name ?n1]}
                                                        {:HasAttr [?e2 :name ?n2]}
                                                        []]} [?e1 ?e2]]} [])
      (transact db [[:db/add 1 :name "Dipper"]
                    [:db/add 2 :name "Mabel"]])
      (expect-> out (is (= [[[1 1] 1] [[1 2] 1] [[2 1] 1] [[2 2] 1]] out)))
      (transact db [[:db/retract 2 :name "Mabel"]])
      (expect-> out (is (= [[[1 2] -1] [[2 1] -1] [[2 2] -1]] out))))))

(deftest test-basic-disjunction
  (let [db (df/create-db {:name {:db/valueType :String} :age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (register-query db "basic-disjunction" '[:find ?e :where (or [?e :name "Mabel"] [?e :name "Dipper"])])
      (transact db [[:db/add 1 :name "Dipper"] [:db/add 1 :age 26]])
      (expect-> out (is (= [[[1] 1]] out)))
      (transact db [{:db/id 2 :name "Mabel" :age 26}])
      (expect-> out (is (= [[[2] 1]] out)))
      (transact db [[:db/retract 2 :name "Mabel"]])
      (expect-> out (is (= [[[2] -1]] out))))))

(deftest test-simple-negation
  (let [db (df/create-db {:name {:db/valueType :String} :age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (register-query db "simple-negation" '[:find ?e :where [?e :name "Mabel"] (not [?e :age 25])])
      (transact db [{:db/id 1 :name "Mabel" :age 25}])
      (transact db [{:db/id 2 :name "Mabel" :age 42}])
      (expect-> out (is (= [[[2] 1]] out)))
      (transact db [[:db/add 2 :age 25]])
      (expect-> out (is (= [[[2] -1]] out))))))

(deftest test-min
  (let [db (df/create-db {:age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (register-query db "min" '[:find ?user (min ?age) :where [?user :age ?age]])
      (transact db [{:db/id 1 :age 12}
                    {:db/id 2 :age 25}])
      (expect-> out (is (= [[[1 12] 1]] out)))
      (transact db [[:db/add 3 :age 5]])
      (expect-> out (is (= [[[3 5] 1] [[1 12] -1]] out))))))

(deftest test-max
  (let [db (df/create-db {:age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (register-query db "max" '[:find ?user (max ?age) :where [?user :age ?age]])
      (transact db [{:db/id 1 :age 12}
                    {:db/id 2 :age 25}])
      (expect-> out (is (= [[[2 25] 1]] out)))
      (transact db [[:db/add 3 :age 35]])
      (expect-> out (is (= [[[3 35] 1] [[2 25] -1]] out))))))

(deftest test-count
  (let [db (df/create-db {:age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (register-query db "count" '[:find (count ?user) :where [?user :age ?age]])
      (transact db [{:db/id 1 :age 12}
                    {:db/id 2 :age 25}])
      (expect-> out (is (= [[[2] 1]] out)))
      (transact db [[:db/add 3 :age 5]])
      (expect-> out (is (= [[[2] -1] [[3] 1]] out)))
      (transact db [[:db/retract 3 :age 5]])
      (expect-> out (is (= [[[2] 1] [[3] -1]] out))))))
