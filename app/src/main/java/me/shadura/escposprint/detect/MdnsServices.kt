package me.shadura.escposprint.detect

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.HashMap

import javax.jmdns.ServiceInfo
import javax.jmdns.impl.DNSIncoming
import javax.jmdns.impl.DNSRecord

import me.shadura.escposprint.L

class MdnsServices {

    private var error = false

    /**
     * @return the last exception that occurred while trying to connect to scanned hosts
     */
    val exception: Exception? = null

    private val hosts: MutableMap<String, String>

    private val services: MutableMap<String, Array<String>>

    init {
        hosts = HashMap()
        services = HashMap()
    }

    private fun makeQuestion(data: String): ByteArray {
        var data = data
        val lastChar = data[data.length - 1]
        if (lastChar == '.') {
            data = data.substring(0, data.length - 1)
        }
        val bytes = ByteBuffer.allocateDirect(data.length + 1)
        val parts = data.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (part in parts) {
            bytes.put(part.length.toByte())
            bytes.put(part.toByteArray())
        }
        bytes.flip()
        val ret = ByteArray(bytes.capacity())
        bytes.get(ret)
        return ret

    }

    private fun makeMessage(data: String): ByteArray {
        val question = makeQuestion(data)
        val message = ByteArray(HEADER.size + question.size + FOOTER.size)
        var pos = 0
        System.arraycopy(HEADER, 0, message, pos, HEADER.size)
        pos = pos + HEADER.size
        System.arraycopy(question, 0, message, pos, question.size)
        pos = pos + question.size
        System.arraycopy(FOOTER, 0, message, pos, FOOTER.size)
        return message
    }

    private fun process(list: MutableMap<String, PrinterRec>, packet: DatagramPacket, service: String) {
        var protocol = "http"
        if (service == IPPS_SERVICE) {
            protocol = "https"
        }
        try {
            val `in` = DNSIncoming(packet)
            if (`in`.numberOfAnswers < 1) {
                L.v("No answer in mDNS response: $packet")
                return
            }
            val answers = `in`.allAnswers
            var iterator: MutableIterator<DNSRecord> = answers.iterator()
            var info: ServiceInfo

            while (iterator.hasNext()) {
                val record = iterator.next()
                if (record is DNSRecord.Address) {
                    info = record.getServiceInfo()
                    val ip = info.hostAddresses[0]
                    hosts[info.name + "." + info.domain + "."] = ip
                    iterator.remove()
                }
            }
            iterator = answers.iterator()
            while (iterator.hasNext()) {
                val record = iterator.next()
                if (record is DNSRecord.Service) {
                    info = record.getServiceInfo()
                    services[info.key] = arrayOf<String>(hosts[info.server]!!, info.port.toString())
                    iterator.remove()
                }
            }

            iterator = answers.iterator()
            while (iterator.hasNext()) {
                val record = iterator.next()
                info = record.serviceInfo
                if (record !is DNSRecord.Text) {
                    continue
                }
                if (info.type != service) {
                    continue
                }
                var rp: String? = info.getPropertyString("rp") ?: continue
                val rps = rp!!.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                try {
                    rp = rps[rps.size - 1]
                } catch (e: Exception) {
                    rp = ""
                    L.e("There was an error when trying to process rp in DNS answers", e)
                }

                L.d("process: qualified name: " + info.qualifiedName)
                val key: String? = info.key ?: {
                    L.e("Couldn't find printer from mDNS datagram: " + info.application + ", " + info.domain + ", " + info.key)
                    null
                }()
                key?.let { key: String ->
                    val p = getPrinterRec(
                            info.name,
                            protocol,
                            services[key]!![0],
                            Integer.parseInt(services[key]!![1]),
                            rp)

                    if (p != null) {
                        L.d("A new printer responded to an mDNS query: $p")
                        list[key] = p
                    } else {
                        L.e("A new printer responded to an mDNS query, but it has no PrinterRec: ignore" +
                                " (info: " + info + ", protocol: " + protocol + ", service: " + services[key]!![0] + ", port: " + Integer.parseInt(services[key]!![1]) + ", queue: " + rp)
                    }
                }
            }
        } catch (e: Exception) {
            println(e.toString())
            L.e("There was an error when trying to process a datagram packet: $packet", e)
        }

    }

    private fun getPrinterRec(nickname: String?, protocol: String?, host: String?, port: Int?, queue: String?): PrinterRec? {
        var nickname = nickname
        if (nickname == null) {
            nickname = "unknown"
        }
        if (protocol == null) {
            return null
        }
        if (host == null) {
            return null
        }
        return if (queue == null) {
            null
        } else PrinterRec(nickname, protocol, host, port!!, queue)

    }

    private fun getPrinters(service: String): Map<String, PrinterRec> {
        val printers = HashMap<String, PrinterRec>()
        try {
            val s: MulticastSocket
            val group: InetAddress
            group = InetAddress.getByName("224.0.0.251")
            s = MulticastSocket(5353)
            s.soTimeout = TIMEOUT
            s.joinGroup(group)
            val packet = makeMessage(service)
            val hi = DatagramPacket(packet, packet.size, group, 5353)
            s.send(hi)
            val buf = ByteArray(65535)
            val recv = DatagramPacket(buf, buf.size)
            error = false
            var passes = 1
            while (!error) {
                try {
                    s.receive(recv)
                    process(printers, recv, service)
                    recv.length = buf.size
                    passes++
                    if (passes > MAX_PASSES) {
                        error = true
                    }
                } catch (e: Exception) {
                    error = true
                    // Socket timeout occur all the time, don't send them back to crashlytics
                    if (e !is SocketTimeoutException) {
                        L.e("There was an error when trying to receive mDNS response", e)
                    }
                }

            }
            //System.out.println(passes);
            s.leaveGroup(group)
            s.close()
        } catch (e: Exception) {
            println(e.toString())
        }

        return printers
    }

    fun scan(): PrinterResult {
        val httpRecs = ArrayList<PrinterRec>()
        val httpsRecs = ArrayList<PrinterRec>()
        httpRecs.addAll(getPrinters(MdnsServices.IPP_SERVICE).values)
        httpsRecs.addAll(getPrinters(MdnsServices.IPPS_SERVICE).values)
        Merger().merge(httpRecs, httpsRecs)

        val result = PrinterResult()
        result.setPrinterRecs(httpsRecs)
        return result
    }

    fun stop() {
        error = true
    }

    companion object {
        const val IPP_SERVICE = "_ipp._tcp.local."

        const val IPPS_SERVICE = "_ipps._tcp.local."

        const val MAX_PASSES = 50

        private val HEADER = byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0)

        private val FOOTER = byteArrayOf(0, 0, 12, 0, 1)

        const val TIMEOUT = 1000
    }
}
