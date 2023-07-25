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

