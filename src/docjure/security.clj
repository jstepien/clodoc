(ns docjure.security)

(def *salt* "change-me")

(if (= *salt* "change-me")
  (throw (Exception. "Change the salt in security.clj.")))

(defn- sha512
  [#^String input]
  (let [md (java.security.MessageDigest/getInstance "SHA-512")]
    (. md update (.getBytes input))
    (apply str (map #(Integer/toHexString (bit-and % 0xff)) (.digest md)))))

(defn sign
  [msg]
  (sha512 (str *salt* msg *salt*)))

(defn signed?
  "Verifies a signature. I hope it prevents timing attacks."
  [msg signature]
  (let [correct (sign msg)]
    (reduce (fn [result [a b]] (and result (= a b)))
            true (map vector correct signature))))
