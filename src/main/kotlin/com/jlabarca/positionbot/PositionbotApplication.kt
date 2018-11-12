package com.jlabarca.positionbot

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import java.io.File
import java.util.*
import org.apache.logging.log4j.LogManager
import org.springframework.http.ResponseEntity
import javax.annotation.PostConstruct
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.RequestBody
import java.io.FileWriter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import java.util.concurrent.ConcurrentLinkedQueue


@EnableScheduling
@SpringBootApplication
@Controller //("/")
class Main {
    companion object {
        private val log = LogManager.getLogger()
    }

    private lateinit var positionBot: PositionBot

    @Value("\${API_KEY}")
    private val apiKey: String? = null

    @Value("\${API_SECRET}")
    private val apiSecret: String? = null

    @Value("\${DEBUG}")
    private val debug: Boolean = false


    @PostConstruct
    fun init() {

        this.positionBot = PositionBot(apiKey, apiSecret)
        var positions = getPositionsJson()
        log.warn("positions size: "+positions.size)
/*
        var ont =  Position("ONT","BTC",
                49f, 0.00055966f, 0.00056566f, 0.00055766f);
        ont.amount = 0.02774f;
        ont.state = Position.PositionState.TAKEN
        positions.add(ont)

        ont =  Position("XVG","BTC",
                49f, 0.00055966f, 0.00056566f, 0.00055766f);
        ont.amount = 0.02774f;
        ont.state = Position.PositionState.TAKEN
        positions.add(ont)

        ont =  Position("BNB","BTC",
                49f, 0.00055966f, 0.00056566f, 0.00055766f);
        ont.amount = 0.02774f;
        ont.state = Position.PositionState.TAKEN
        positions.add(ont)

        ont =  Position("ETH","BTC",
                49f, 0.00055966f, 0.00056566f, 0.00055766f);
        ont.amount = 0.02774f;
        ont.state = Position.PositionState.TAKEN
        positions.add(ont)

        ont =  Position("OMG","BTC",
                49f, 0.00055966f, 0.00056566f, 0.00055766f);
        ont.amount = 0.02774f;
        ont.state = Position.PositionState.TAKEN
        positions.add(ont)
        log.warn("positions size: "+positions.size)
        savePositionJson(positions)
*/

        this.positionBot.positions = positions;
    }

    fun getPositionsJson(initPaused: Boolean = true): ConcurrentLinkedQueue<Position> {
        //Use the JSON File included as a resource
        val classLoader = Position::class.java.classLoader
        //val dataFile = File(classLoader.getResource("positions.json").file)
        val dataFile = File("positions.json")
        //Simple example of getting the Sleep Objects from that JSON
        var positions = ConcurrentLinkedQueue<Position>()
        JsonConfigReader<Position>(dataFile, Position::class.java) //Got the Stream
                .forEachRemaining({ pos ->
                    if(initPaused)
                        pos.state = Position.PositionState.PAUSED
                    positions.add(pos)
                    pos.init()
                })
        log.warn(positions.toString())
        return positions
    }

    fun savePositionJson(positions: ConcurrentLinkedQueue<Position>){
        val mapper = ObjectMapper()
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        FileWriter("positions.json").use { file ->
            file.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(positions))
        }
        val classLoader = Position::class.java.classLoader
        //val dataFile = File(classLoader.getResource("positions.json").file)
        val dataFile = File("positions.json")
        mapper.writeValue(dataFile, positions)
        log.debug("Successfully Copied JSON Object to File...")

    }
    // tick every 3 seconds
    @Scheduled(fixedRate = 1000)
    fun schedule() {
        this.positionBot.tick()
    }

    @Scheduled(fixedRate = 10000)
    fun saveJson() {
        savePositionJson(positionBot.positions)
    }

    /*@Value("${positions.message:test}")
     private String message = "Hello World";
    */
    @RequestMapping("/")
    fun welcome(model: Model):String {
        log.info("SIZE:" + positionBot.getPositions().size)
        model.addAttribute("serverTime", Date())
        model.addAttribute("positions", positionBot!!.getPositions())
        return "positions"
    }

    @RequestMapping("/getPositions")
    fun getPositions():ResponseEntity <Any>{
        return ResponseEntity(this.positionBot.positions, HttpStatus.OK)
    }

    @RequestMapping(value = "/panicSell", method = arrayOf(RequestMethod.POST))
    fun getPositions(@RequestBody position: Position):ResponseEntity <Any>{
        log.warn("panicSell: "+position.id)
        try {
            position.stopLoss()
        }catch (e: Exception) {
            log.error("panicSell fallido: "+position.id)
            e.printStackTrace()
        }

        return ResponseEntity(HttpStatus.OK)
    }

    @RequestMapping(value = "/addPosition", method = arrayOf(RequestMethod.POST))
    fun addPosition(@RequestBody position: Position):ResponseEntity <Any>{
        log.warn("addPosition: "+position.id)
        try {
            position.init()
            position.history = LinkedList<Float>()
            positionBot.positions.add(position)
        }catch (e: Exception) {
            log.error("addPosition fallido: "+position.id)
            e.printStackTrace()
        }

        return ResponseEntity(HttpStatus.OK)
    }

    @RequestMapping(value = "/removePosition", method = arrayOf(RequestMethod.POST))
    fun removePosition(@RequestBody position: Position):ResponseEntity <Any>{
        log.warn("removePosition: "+position.id)
        try {
            var pos = positionBot.positions.filter { s -> s.id == position.id }.single()
            positionBot.positions.remove(pos)
        }catch (e: Exception) {
            log.error("removePosition fallido: "+position.id)
            e.printStackTrace()
        }

        return ResponseEntity(HttpStatus.OK)
    }

    @RequestMapping(value = "/editPosition", method = arrayOf(RequestMethod.POST))
    fun editPosition(@RequestBody position: Position):ResponseEntity <Any>{
        log.warn("addPosition: "+position.id)
        var pos = positionBot.positions.filter { s -> s.id == position.id }.single()
        //positionBot.positions.set(positionBot.positions.indexOf(pos), position)
        if(pos.tradeCurrency != position.tradeCurrency || pos.baseCurrency != position.baseCurrency)
            pos.history = LinkedList<Float>()
        pos.buyPrice = position.buyPrice;
        pos.sellPrice = position.sellPrice;
        pos.stopLossPrice = position.stopLossPrice;
        pos.tradeCurrency = position.tradeCurrency;
        pos.baseCurrency = position.baseCurrency;
        pos.state = position.state;
        pos.quantity = position.quantity;
        pos.percentage = position.percentage;
        pos.percentageOnBuy = position.percentageOnBuy;

        try {
            position.init()
            //positionBot.positions.add(position)
        }catch (e: Exception) {
            log.error("addPosition fallido: "+position.id)
            e.printStackTrace()
        }

        return ResponseEntity(HttpStatus.OK)
    }

}

fun main(args: Array<String>) {
    runApplication<Main>(*args)
}
