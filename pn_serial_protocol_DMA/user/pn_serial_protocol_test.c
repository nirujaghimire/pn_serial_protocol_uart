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

static void receiveCallback(uint32_t id, uint8_t* data, uint16_t size){
	printf("0x%0x : ",id);
	for(int i=0;i<size;i++)
		printf("%c",data[i]);
	printf("\n");
}

static void run(){
	StaticSerialProtocol.init(&huart1,&hcrc,receiveCallback);

	char bytes[]="Hello this Niruja Speaking, Peter!!!";

	uint32_t tick = HAL_GetTick();
	while(1){
		uint32_t tock = HAL_GetTick();
		if(tock-tick>1000){
			StaticSerialProtocol.transmit(0x11,(uint8_t*)bytes,sizeof(bytes));
			tock = tick;
		}

		StaticSerialProtocol.loop();
	}

}


struct SerialProtocolTest StaticSerialProtocolTest = {
		.run = run
};

