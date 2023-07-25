# pn_serial_protocol_uart
 
This protocol is for UART communication using DMA in receive.   
Pros of this communication protocol are:  
→It is faster and more reliable  
→In case of error it retries and recovers the communication  
→It uses call back  

# main.java  
```rb
import com.fazecast.jSerialComm.SerialPort;
import tools.SerialUART;

public class Main {
    static volatile boolean transmitted = true;
    public static void test(SerialUART uart){
        uart.enableCanMode();
        uart.setCanReceiveCallback((id, bytes) -> {
            System.out.printf("0x%x (%d) : ",id,bytes.length);
            for (byte aByte : bytes)
                System.out.printf("%c", aByte);
            System.out.println();
            transmitted = true;
        });

        uart.setCanTransmitCallback(status -> {
            if(status != SerialUART.CanTransmitStatus.SUCCESS)
                System.out.println("failed");
            else
               System.out.println("success");
        });
        System.out.println(uart.connect() ? "Connected" : "Connection failed");
        transmitted = true;
        byte[] bytes = "Yes this is Peter Speaking, Niruja!!!".getBytes();
        for(int i=1;i<=(10);i++) {
            while (!transmitted)
                Thread.onSpinWait();
            System.out.printf("Sending : 0x%x => ",i+1);
            transmitted = false;
            uart.send(i+1, bytes);
        }
    }

    public static void main(String[] args) {
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports == null)
            return;
        if (ports.length == 0)
            return;
        SerialPort port = null;
        for (SerialPort p : ports) {
            System.out.println(p.getDescriptivePortName());
            if(p.getDescriptivePortName().contains("COM4"))
                port = p;
        }

        if(port==null) {
            System.out.println("NULL PORT");
            return;
        }
        SerialUART uart = new SerialUART(port, 115200);
        test(uart);
    }
}
```

# main.c
```rb
int main(void)
{
  //Default Initialization...
	
	// Code before while loop
	#include "pn_serial_protocol.h"

  StaticSerialProtocolTest.run();
  while (1){}

}
```

# pn_serial_protocol_test.c
```rb
/*
 * pn_serial_protocol_test.c
 *
 *  Created on: Jul 19, 2023
 *      Author: NIRUJA
 */

#include "pn_serial_protocol.h"
#include "main.h"

extern UART_HandleTypeDef huart1;
extern CRC_HandleTypeDef hcrc;

void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart) {
	StaticSerialProtocol.receiveRxCpltCallback();
}

static volatile uint8_t data_received = 0;
static void receiveCallback(uint32_t id, uint8_t *data, uint16_t size) {
	printf("0x%0x : ", id);
	for (int i = 0; i < size; i++)
		printf("%c", (char) data[i]);
	printf("\n");
	data_received = 1;
}

static void run() {
	StaticSerialProtocol.init(&huart1, &hcrc, receiveCallback);

	printf("This is INIT\n");
	char bytes[] = "Hello this is Niruja Speaking, Peter!!!";
	while (1) {
		if (data_received) {
			data_received = 0;
			StaticSerialProtocol.transmit(0x11, (uint8_t*) bytes,
					sizeof(bytes));
		}

		StaticSerialProtocol.loop();
	}

}

struct SerialProtocolTest StaticSerialProtocolTest = { .run = run };
```

# Output of JAVA
```rb
C:\Users\peter\.jdks\openjdk-20\bin\java.exe "-javaagent:C:\Program Files\JetBrains\IntelliJ IDEA 2022.2.3\lib\idea_rt.jar=52082:C:\Program Files\JetBrains\IntelliJ IDEA 2022.2.3\bin" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -classpath C:\Users\peter\OneDrive\Desktop\Github\pn_serial_protocol\pn_serial_protocol_java\out\production\pn_serial_protocol_java;C:\Users\peter\OneDrive\Desktop\Github\pn_serial_protocol\pn_serial_protocol_java\lib\jSerialComm-2.9.2.jar Main
Silicon Labs CP210x USB to UART Bridge (COM4)
Connected
Sending : 0x2 => success
0x11 (40) : Hello this is Niruja Speaking, Peter!!! 
Sending : 0x3 => success
0x11 (40) : Hello this is Niruja Speaking, Peter!!! 
Sending : 0x4 => success
0x11 (40) : Hello this is Niruja Speaking, Peter!!! 
Sending : 0x5 => success
0x11 (40) : Hello this is Niruja Speaking, Peter!!! 
Sending : 0x6 => success
0x11 (40) : Hello this is Niruja Speaking, Peter!!! 
Sending : 0x7 => success
0x11 (40) : Hello this is Niruja Speaking, Peter!!! 
Sending : 0x8 => success
0x11 (40) : Hello this is Niruja Speaking, Peter!!! 
Sending : 0x9 => success
0x11 (40) : Hello this is Niruja Speaking, Peter!!! 
Sending : 0xa => success
0x11 (40) : Hello this is Niruja Speaking, Peter!!! 
Sending : 0xb => success
0x11 (40) : Hello this is Niruja Speaking, Peter!!!
```

# Output of STM32
```rb
This is INIT
0x2 : Yes this is Peter Speaking, Niruja!!!
0x3 : Yes this is Peter Speaking, Niruja!!!
0x4 : Yes this is Peter Speaking, Niruja!!!
0x5 : Yes this is Peter Speaking, Niruja!!!
0x6 : Yes this is Peter Speaking, Niruja!!!
0x7 : Yes this is Peter Speaking, Niruja!!!
0x8 : Yes this is Peter Speaking, Niruja!!!
0x9 : Yes this is Peter Speaking, Niruja!!!
0xa : Yes this is Peter Speaking, Niruja!!!
0xb : Yes this is Peter Speaking, Niruja!!!
```
