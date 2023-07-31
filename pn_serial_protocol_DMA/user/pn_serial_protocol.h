/*
 * UART.h
 *
 *  Created on: Jan 25, 2023
 *      Author: NIRUJA
 */

#ifndef PN_SERIAL_PROTOCOL_H_
#define PN_SERIAL_PROTOCOL_H_

//#include "stm32f1xx_hal.h"
//#include "stdio.h"
#include "main.h"

struct SerialProtocolControl {
	/**
	 * This is called at beginning to initiate
	 * @param huart	: UART handler
	 * @param hcrc	: CRC handler
	 * @param receiveCallbackFunc	: receive callback function
	 */
	void (*init)(UART_HandleTypeDef *huart, CRC_HandleTypeDef *hcrc,
			void (*receiveCallbackFunc)(uint32_t, uint8_t*, uint16_t));

	/**
	 * This transmits data to PC
	 * @param id	: CAN ID
	 * @param bytes	: bytes to be send
	 * @param len	: Length of data to be sent
	 */
	int (*transmit)(uint32_t id, uint8_t *bytes, uint16_t len);

	/**
	 * This method should be called in DMA callback
	 */
	void (*receiveRxCpltCallback)();

	/**
	 * This checks wheather flash mode is enabled or not
	 */
	int (*isFlashModeEnabled)();

	/**
	 * This is uart loop
	 */
	void (*loop)();

};

extern struct SerialProtocolControl StaticSerialProtocol;

struct SerialProtocolTest {
	void (*run)();
};
extern struct SerialProtocolTest StaticSerialProtocolTest;

#endif /* PN_SERIAL_PROTOCOL_H_ */
