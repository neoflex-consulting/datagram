package test

import org.apache.activemq.ActiveMQConnectionFactory
import javax.jms.*
import java.text.SimpleDateFormat

def brokerUrl = 'tcp://localhost:61616'
def destination = 'EtlEvents'

def getMessage(environmentName, eventType, emitterType, emitterId, seqNumber, message) {
    def jsonTimestampParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    jsonTimestampParser.setTimeZone(TimeZone.getTimeZone("UTC"))
    def timestamp = jsonTimestampParser.format(new Date())
    return """{
        "_type_": "etlrt.RuntimeEvent",
        "emitterType": "${emitterType}",
        "emitterId": "${emitterId}",
        "timestamp": "${timestamp}",
        "previousStatus": "NONE",
        "id": "${emitterId}-${seqNumber}-${timestamp}",
        "nextStatus": "INPROGRESS",
        "environmentName": "${environmentName}",
        "seqNumber": ${seqNumber},
        "eventType": "${eventType}",
        "transformationName": "",
        "applicationName": "",
        "stepName": "",
        "statisticName": "",
        "rddName": "",
        "message": "${message}"
        }"""
}
new ActiveMQConnectionFactory(brokerURL: brokerUrl).createConnection().with {
    start()
    createSession(false, Session.AUTO_ACKNOWLEDGE).with {
        def message = createTextMessage(getMessage("test", "START", "Execution", "execution-1", 1, ""))
        message.with {
            jMSDeliveryMode = DeliveryMode.PERSISTENT
            jMSCorrelationID = UUID.randomUUID().toString()
        }
        createProducer().send(createTopic(destination), message)
    }
    close()
}
