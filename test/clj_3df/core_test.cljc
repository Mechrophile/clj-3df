(ns clj-3df.core-test
  (:require
   #?(:clj  [clojure.test :refer [deftest is testing run-tests]]
      :cljs [cljs.test :refer-macros [deftest is testing run-tests]])
   #?(:clj  [clojure.core.async :as async :refer [<! >! go-loop]]
      :cljs [cljs.core.async :as async :refer [<! >!]])
   [clj-3df.core :as df :refer [exec! create-db-inputs create-debug-conn register-query register-plan transact]])
  #?(:cljs (:require-macros [clj-3df.core :refer [exec!]]
                            [cljs.core.async :refer [go-loop]])))


(defn- debug-conn []
  (let [conn (create-debug-conn "ws://127.0.0.1:6262")]
    conn))

(comment

  (def conn (debug-conn))

  (def db (df/create-db {:parent/child {:db/valueType :Eid}
                         :create       {:db/valueType :Eid}
                         :read         {:db/valueType :Eid}
                         :update       {:db/valueType :Eid}
                         :delete       {:db/valueType :Eid}}))

  (exec! conn
    (df/create-db-inputs db))

  (def rules
    '[[(read? ?user ?obj) (or [?user :read ?obj]
                              (and [?parent :parent/child ?obj]
                                   (read? ?user ?parent)))]])

  (exec! conn
    (register-query
     db "rba"
     '[:find ?user ?obj :where (read? ?user ?obj)]
     rules))

  (exec! conn
    (transact db [[:db/add 100 :read 901]]))

  (exec! conn
    (transact db [[:db/add 901 :parent/child 902]]))

  (exec! conn
    (transact db [[:db/retract 100 :read 901]]))

  (exec! conn
    (transact db [[:db/retract 901 :parent/child 902]]))

  (exec! conn
    (transact db [[:db/add 100 :read 901]])
    (transact db [[:db/add 901 :parent/child 902]])
    (register-query db "rba" '[:find ?user ?obj :where (read? ?user ?obj)] rules))

  )

;; @TODO until unregister is available, the server has to be restarted
;; between tests to avoid interference

;; @TODO
;; Naming guidelines: Tests in here should be named in such a way,
;; that there is a one-to-one correspondence between tests and actual,
;; user-facing query engine features.

;; @TODO this test-suite should mirror the datascript tests

(deftest test-basic-conjunction
  (let [name "basic-conjunction"
        db   (df/create-db {:name {:db/valueType :String} :age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (create-db-inputs db)
      (register-query db name '[:find ?e ?age :where [?e :name "Mabel"] [?e :age ?age]])
      (transact db [[:db/add 1 :name "Dipper"] [:db/add 1 :age 26]])
      (transact db [{:db/id 2 :name "Mabel" :age 26}])
      (expect-> out (is (= [name [[[2 26] 1 1]]] out)))
      (transact db [[:db/retract 2 :name "Mabel"]])
      (expect-> out (is (= [name [[[2 26] 2 -1]]] out))))))

(deftest test-multi-conjunction
  (let [name "multi-conjunction"
        db   (df/create-db {:name {:db/valueType :String} :age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (create-db-inputs db)
      (register-query db name '[:find ?e1 ?e2
                                :where
                                [?e1 :name ?name] [?e1 :age ?age]
                                [?e2 :name ?name] [?e2 :age ?age]])
      (transact db [{:db/id 1 :name "Dipper" :age 26}
                    {:db/id 2 :name "Mabel" :age 26}
                    {:db/id 3 :name "Soos" :age 32}])
      (expect-> out (is (= [name [[[1 1] 0 1] [[2 2] 0 1] [[3 3] 0 1]]] out)))
      (transact db [[:db/retract 2 :name "Mabel"]])
      (expect-> out (is (= [name [[[2 2] 1 -1]]] out))))))

(deftest test-cartesian
  (let [name "cartesian"
        db   (df/create-db {:name {:db/valueType :String}})]
    (exec! (debug-conn)
      (create-db-inputs db)
      ;; (register-query db name '[:find ?e1 ?e2 :where [?e1 :name ?n1] [?e2 :name ?n2]])
      (register-plan db name '{:Project
                               [[?e1 ?e2]
                                {:Join [[]
                                        {:MatchA [?e1 :name ?n1]}
                                        {:MatchA [?e2 :name ?n2]}]}]} [])
      (transact db [[:db/add 1 :name "Dipper"]
                    [:db/add 2 :name "Mabel"]])
      (expect-> out (is (= [name [[[1 1] 0 1] [[1 2] 0 1] [[2 1] 0 1] [[2 2] 0 1]]] out)))
      (transact db [[:db/retract 2 :name "Mabel"]])
      (expect-> out (is (= [name [[[1 2] 1 -1] [[2 1] 1 -1] [[2 2] 1 -1]]] out))))))

(deftest test-basic-disjunction
  (let [name "basic-disjunction"
        db   (df/create-db {:name {:db/valueType :String} :age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (create-db-inputs db)
      (register-query db name '[:find ?e :where (or [?e :name "Mabel"] [?e :name "Dipper"])])
      (transact db [[:db/add 1 :name "Dipper"] [:db/add 1 :age 26]])
      (expect-> out (is (= [name [[[1] 0 1]]] out)))
      (transact db [{:db/id 2 :name "Mabel" :age 26}])
      (expect-> out (is (= [name [[[2] 1 1]]] out)))
      (transact db [[:db/retract 2 :name "Mabel"]])
      (expect-> out (is (= [name [[[2] 2 -1]]] out))))))

(deftest test-conjunction-and-disjunction
  (let [name "conjunction-and-disjunction"
        db   (df/create-db {:name {:db/valueType :String} :age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (create-db-inputs db)
      (register-query db name '[:find ?e
                                :where
                                [?e :name "Mabel"]
                                (or [?e :age 14]
                                    [?e :age 12])])
      (transact db [[:db/add 1 :name "Dipper"] [:db/add 1 :age 14]
                    {:db/id 2 :name "Mabel" :age 14}
                    {:db/id 3 :name "Mabel" :age 12}
                    {:db/id 4 :name "Mabel" :age 18}])
      (expect-> out (is (= [name [[[2] 0 1] [[3] 0 1]]] out)))
      (transact db [[:db/retract 2 :name "Mabel"]
                    [:db/retract 3 :age 12]])
      (expect-> out (is (= [name [[[2] 1 -1] [[3] 1 -1]]] out))))))

(deftest test-simple-negation
  (let [name "simple-negation"
        db   (df/create-db {:name {:db/valueType :String} :age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (create-db-inputs db)
      (register-query db name '[:find ?e :where [?e :name "Mabel"] (not [?e :age 25])])
      (transact db [{:db/id 1 :name "Mabel" :age 25}])
      (transact db [{:db/id 2 :name "Mabel" :age 42}])
      (expect-> out (is (= [name [[[2] 1 1]]] out)))
      (transact db [[:db/add 2 :age 25]])
      (expect-> out (is (= [name [[[2] 2 -1]]] out))))))

(deftest test-min
  (let [name "min"
        db   (df/create-db {:age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (create-db-inputs db)
      (register-query db name '[:find (min ?age) :where [?user :age ?age]])
      (transact db [{:db/id 1 :age 12}
                    {:db/id 2 :age 25}])
      (expect-> out (is (= [name [[[12] 0 1]]] out)))
      (transact db [[:db/add 3 :age 5]])
      (expect-> out (is (= [name [[[5] 1 1] [[12] 1 -1]]] out))))))

(deftest test-max
  (let [name "max"
        db   (df/create-db {:age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (create-db-inputs db)
      (register-query db name '[:find (max ?age) :where [?user :age ?age]])
      (transact db [{:db/id 1 :age 12}
                    {:db/id 2 :age 25}])
      (expect-> out (is (= [name [[[25] 0 1]]] out)))
      (transact db [[:db/add 3 :age 35]])
      (expect-> out (is (= [name [[[35] 1 1] [[25] 1 -1]]] out))))))

(deftest test-count
  (let [name "count"
        db   (df/create-db {:age {:db/valueType :Number}})]
    (exec! (debug-conn)
      (create-db-inputs db)
      (register-query db name '[:find (count ?user) :where [?user :age ?age]])
      (transact db [{:db/id 1 :age 12}
                    {:db/id 2 :age 25}])
      (expect-> out (is (= [name [[[2] 0 1]]] out)))
      (transact db [[:db/add 3 :age 5]])
      (expect-> out (is (= [name [[[2] 1 -1] [[3] 1 1]]] out)))
      (transact db [[:db/retract 3 :age 5]])
      (expect-> out (is (= [name [[[2] 2 1] [[3] 2 -1]]] out))))))

(deftest test-registration
  (testing "queries should produce results for previously transacted data"
    (let [name  "rba"
          db    (df/create-db {:parent/child {:db/valueType :Eid}
                               :create       {:db/valueType :Eid}
                               :read         {:db/valueType :Eid}
                               :update       {:db/valueType :Eid}
                               :delete       {:db/valueType :Eid}})
          rules '[[(read? ?user ?obj) (or [?user :read ?obj]
                                          (and [?parent :parent/child ?obj]
                                               (read? ?user ?parent)))]]
          conn  (debug-conn)]
      (exec! conn
        (create-db-inputs db)
        (transact db [[:db/add 100 :read 901]
                      [:db/add 901 :parent/child 902]]))
      (Thread/sleep 1000)
      (exec! conn
        (register-query db name '[:find ?user ?obj
                                  :where (read? ?user ?obj)] rules)
        (expect-> out (is (= [name [[[100 901] 0 1] [[100 902] 0 1]]] out)))))))
