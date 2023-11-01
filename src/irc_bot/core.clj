(ns irc-bot.core
  (:import (java.net Socket SocketException)
           (java.io PrintWriter InputStreamReader BufferedReader)))

(def testnet {:name "testnet.ergo.chat" :port 6667})

(def servers [{:name "testnet.ergo.chat" :port 6667}
             {:name "irc.libera.chat" :port 6667}])

(def user {:name "Clojure Bot" :nick "clj872"})

(declare conn-handler)

(defn connect [server]
  (let [socket (Socket. (:name server) (:port server))
        in     (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out    (PrintWriter. (.getOutputStream socket))
        conn   (atom {:in in :out out :socket socket})]
    (doto (Thread. #(try (conn-handler conn)
                         (catch SocketException se
                           (println (str "Caught exception: " (.getMessage se))))))
      (.start))
    conn))

(defn write [conn msg]
  (doto (:out @conn)
    (.println (str msg "\r"))
    (.flush)))

(defn login [conn user]
  (write conn (str "NICK " (:nick user)))
  (write conn (str "USER " (:nick user) " 0 * :" (:name user))))

(defn connected? [conn] (.isConnected conn))

(defn conn-handler [conn]
  (println "connection handler")
  (login conn user)
  (while (and (connected? (:socket @conn)) (nil? (:exit @conn)))
    (let [msg (.readLine (:in @conn))]
      (println msg)
      (cond
        (re-find #"^ERROR :Closing Link:" msg)
        (swap! conn merge {:exit true})
        (re-find #"^PING" msg)
        (write conn (str "PONG " (re-find #":.*" msg)))))))

(defn disconnected? [conn] (.isClosed conn))

(def connections (doall (map connect servers)))

(comment
  (defn disconnect-servers [conn]
    (doseq [s conn]
      (if (not (disconnected? (:socket @s)))
        (do
          (println "Closing socket : " s)
          (.close (:socket @s)))
        (println "Not connected to any server"))))

  (disconnect-servers connections))
