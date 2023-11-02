(ns irc-bot.core
  (:import (javax.net.ssl SSLSocketFactory)
           (java.net Socket SocketException)
           (java.io PrintWriter InputStreamReader BufferedReader)))


(def servers [{:name "irc.hashbang.sh" :port 6697 :tls true
               :channels ["#!" "#bots"]}
              {:name "testnet.ergo.chat" :port 6667 :tls false
               :channels ["#bots" "#0"]}
              {:name "irc.tilde.chat" :port 6697 :tls true
               :channels ["#bots" "#meta"]}
              {:name "irc.libera.chat" :port 6667 :tls false
               :channels ["#bots" "#clojure"]}])

(def user {:name "Clojure Bot" :nick "cljpt"})

(declare conn-handler)

(defn connect [server]
  (let [socket (if (true? (:tls server))
                 (.createSocket (SSLSocketFactory/getDefault)
                                (Socket. (:name server) (:port server))
                                (:name server) (:port server) true)
                 (Socket. (:name server) (:port server)))
        channels (:channels server)
        in     (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out    (PrintWriter. (.getOutputStream socket))
        conn   (atom {:in in :out out :socket socket :channels channels})]
    (doto (Thread. #(try (conn-handler conn)
                         (catch SocketException se
                           (println (str "Caught exception: " (.getMessage se))))))
      (.start))
    conn))

(defn write [conn msg]
  (doto (:out @conn)
    (.println (str msg "\r"))
    (.flush)))

(defn join [conn chan]
  (write conn (str "JOIN " chan)))

(defn login [conn user]
  (write conn (str "NICK " (:nick user)))
  (write conn (str "USER " (:nick user) " 0 * :" (:name user))))

(defn connected? [conn] (.isConnected conn))

(defn conn-handler [conn]
  (println "Connecting to:" (.toString (.getRemoteSocketAddress (:socket @conn))))
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
          (println "Closing TCP socket connection:" (.toString (.getRemoteSocketAddress (:socket @s))))
          (.close (:socket @s)))
        (println "No established connection to:" (.toString (.getRemoteSocketAddress (:socket @s)))))))

  (defn join-all-channels [conns]
    (for [c conns
          ch (:channels @c)]
      (join c ch)))

  (disconnect-servers connections)
  (join-all-channels connections))
