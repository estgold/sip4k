package ru.stech.rtp

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.EventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.util.internal.SocketUtils
import kotlinx.coroutines.CoroutineDispatcher
import ru.stech.BotClient
import ru.stech.quiet.QuietAnalizer
import kotlin.random.Random

class RtpSession(
    val user: String,
    val listenPort: Int,
    private val rtpNioEventLoopGroup: EventLoopGroup,
    private val dispatcher: CoroutineDispatcher,
    private val botClient: BotClient
) {
    private val qa = QuietAnalizer()
    private lateinit var future: ChannelFuture
    var remoteHost: String? = null
    var remotePort: Int? = null
    private var seqNum: Short = 10000
    private var time = 3000
    private val ssrc = Random.Default.nextInt()

    fun resetQuietAnalizer() {
        qa.reset()
    }

    /**
     * Start listening responses from remote rtp-server
     */
    fun start() {
        val rtpClientBootstrap = Bootstrap()
        rtpClientBootstrap
            .channel(NioDatagramChannel::class.java)
            .group(rtpNioEventLoopGroup)
            .handler(object : ChannelInitializer<NioDatagramChannel>() {
                @Throws(Exception::class)
                override fun initChannel(ch: NioDatagramChannel) {
                    ch.pipeline().addLast(RtpChannelInboundHandler(
                        user = user,
                        botClient = botClient,
                        qa = qa
                    ))
                }
            })
        future = rtpClientBootstrap.bind(listenPort).syncUninterruptibly()
    }

    fun sendRtpData(data: ByteArray) {
        val rtpPacket = RtpPacket()
        rtpPacket.version = 2
        rtpPacket.payloadType = 8
        rtpPacket.sequenceNumber = seqNum
        rtpPacket.payload = data
        rtpPacket.timeStamp = time
        time += data.size
        seqNum = seqNum.plus(1).toShort()
        rtpPacket.SSRC = 0x262126F7

        future.channel().writeAndFlush(
            DatagramPacket(Unpooled.copiedBuffer(rtpPacket.rawData),
            SocketUtils.socketAddress(remoteHost, remotePort!!)
        )).syncUninterruptibly()
    }

    /**
     * Stop listening responses from rtp-server
     */
    fun stop() {
        future.channel().close().syncUninterruptibly()
    }
}