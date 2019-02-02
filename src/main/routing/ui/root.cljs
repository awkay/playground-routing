(ns routing.ui.root
  (:require
    [fulcro.client.dom :as dom :refer [div ul li p h3]]
    [fulcro.client.primitives :as prim :refer [defsc]]
    [fulcro.client.routing :as fr :refer [defsc-router]]
    [fulcro.client.mutations :as m :refer [defmutation]]
    [fulcro.incubator.io-progress :as iop]
    [fulcro.incubator.pessimistic-mutations :as pm]
    [taoensso.timbre :as log]
    [fulcro.client.data-fetch :as df]))

(defn task-set-path
  ([id field] [:task-set/id id field])
  ([id-or-props]
   (if (map? id-or-props)
     [:task-set/id (:task-set/id id-or-props)]
     [:task-set/id id-or-props])))

(defsc TaskSet [this {:task-set/keys [id title] :as props}]
  {:query [:task-set/id :task-set/title]
   :ident (fn [] (task-set-path props))}
  (dom/div
    (dom/p "Task set " title)))

(def ui-task-set (prim/factory TaskSet {:keyfn :task-set/id}))

;; 1. See the route change
;; 2. probably don't want them to see a "stale" task set (wrong route)
;; 3. Probably want to show loader IFF network I/O, AND it's been "too long" (avoid flicker).
(defn task-screen-path
  ([] [:SCREEN/task-sets :singleton])
  ([field] [:SCREEN/task-sets :singleton field]))

(defmutation show-task-set
  "Link a task set into the task set screen (if it already is loaded), otherwise initiate the load, and link it in
  once it has arrived."
  [{:keys [task-set/id]}]
  (action [{:keys [state] :as env}]
    (let [task-set-missing? (not (boolean (get-in @state (task-set-path id :task-set/title))))]
      (swap! state (fn [s]
                     (cond-> (fr/set-route* s :root-router (task-screen-path))
                       (not task-set-missing?) (assoc-in (task-screen-path :task-sets/current-task-set) (task-set-path id))
                       task-set-missing? (assoc-in (task-screen-path :task-sets/current-task-set) nil))))
      (when task-set-missing?
        (df/load-action env (task-set-path id) TaskSet
          {:marker  (task-screen-path)
           :refresh [:task-sets/current-task-set]
           :target  (task-screen-path :task-sets/current-task-set)}))))
  (remote [env]
    ;; Tell Fulcro to scan the "load queue" for loads. OK to use even if you didn't queue things
    (df/remote-load env)))


(defsc TaskSetScreen [this {:keys [task-sets/current-task-set] :as props}]
  {:query              [{:task-sets/current-task-set (prim/get-query TaskSet)}
                        :router/screen
                        [df/marker-table '_]]
   :componentDidUpdate (fn [pp ps] (iop/update-loading-visible! this pp {:timeout 60}))
   :ident              (fn [] (task-screen-path))
   :initial-state      {:router/screen :SCREEN/task-sets}}
  (let [loading-visible? (prim/get-state this :loading-visible?)]
    (dom/div
      (if (:task-set/title current-task-set)
        (ui-task-set current-task-set)
        (when loading-visible?
          (dom/div "Loading..."))))))

(defsc HomeScreen [this props]
  {:query         [:router/screen]
   :ident         (fn [] [:SCREEN/home :singleton])
   :initial-state {:router/screen :SCREEN/home}}
  (dom/div "Home"))

(def ui-home-screen (prim/factory HomeScreen {:keyfn :id}))

(defsc-router Router [_ props]
  {:ident          (fn [] [(:router/screen props) :singleton])
   :default-route  HomeScreen
   :router-id      :root-router
   :router-targets {:SCREEN/task-sets TaskSetScreen
                    :SCREEN/home      HomeScreen}})

(def ui-router (prim/factory Router))

(defsc Root [this {:keys [:root/router] :as props}]
  {:query         [{:root/router (prim/get-query Router)}]
   :initial-state {:root/router {}}}
  (dom/div
    (dom/button {:onClick #(prim/transact! this `[(fr/set-route ~{:router :root-router
                                                                  :target [:SCREEN/home :singleton]})])}
      "Home")
    (dom/button {:onClick #(prim/transact! this `[(show-task-set ~{:task-set/id 1})])}
      "Task Set 1")
    (dom/button {:onClick #(prim/transact! this `[(show-task-set ~{:task-set/id 2})])}
      "Task Set 2")

    (ui-router router)))


