/*
 * adapter.c
 *
 *  Created on: Dec 29, 2022
 *      Author: NIRUJA GHIMIRE
 */

#include "adapter.h"
#include "string.h"
#include "stdlib.h"
#include "uart.h"
#define SEND_TIME_OUT 1000

extern UART_HandleTypeDef huart1;
extern CRC_HandleTypeDef hcrc;

static void console(const char *title, const char *msg) {
	printf("%s:: %s\n", title, msg);
}

//void HAL_UART_RxCpltCallback(UART_HandleTypeDef *huart) {
//	uart_receiveRxCpltCallback();
//}

void receive(uint32_t id, uint8_t *bytes, uint16_t len) {
	printf("0x%x : (%d)\n", (int) id, len);
	uart_transmit(id, bytes, len);
	//	can_com_addMessage_tx(LINK_ID, id, bytes, len, 1);
//	uint32_t prev_tick = HAL_GetTick();
//	int count = 0;
//	uint16_t new_size = 0;

//	while (1) {
//		if (HAL_GetTick() - prev_tick > SEND_TIME_OUT)
//			break;
//		new_size = (len - count);
//		if (new_size > 8)
//			new_size = 8;
//		if (new_size <= 0)
//			break;
//		if(!canTransmitted)
//			continue;
////		printf("%d\n",count);
//		if (canSend(id, bytes + count, new_size)) {
//			prev_tick = HAL_GetTick();
//			count += new_size;
//			canTransmitted = 0;
//		}
//	}
}

////////////////////////////////////MAIN CODE///////////////////////////////////////////
uint8_t data1[] = { 1, 2, 3, 4, 5 };
uint8_t data2[] = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, [99] = 12 };
/*
 * This function is called at the beginning of our code
 * @param adapter_huart		: handler of UART used for receiving and transmitting data
 */
void init() {
	uart_init(&huart1, &hcrc, receive);

	console("INIT FROM ADAPTER", "SUCCESS");
}

uint32_t id = 0;
/**
 * This function is called repeatedly
 */
void loop() {
//	id++;

//	static uint32_t prevTick = 0;
//	baseLayerLoop();
//	mapLoop();
//	if ((HAL_GetTick() - prevTick) >= 10) {
//		printf("0x%02x\n",(int)id);
//		can_com_addMessage_tx(LINK_ID, id++, data2, 100, 1);
//		prevTick = HAL_GetTick();
//	}
//	if (id < 10)
//		uart_transmit(id, data1, 5);

	uart_loop();

//	HAL_Delay(1);
}

