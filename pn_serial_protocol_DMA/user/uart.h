/*
 * UART.h
 *
 *  Created on: Jan 25, 2023
 *      Author: NIRUJA
 */

#ifndef UART_H_
#define UART_H_

#include "main.h"

void uart_init(UART_HandleTypeDef*,CRC_HandleTypeDef*,void (*)(uint32_t, uint8_t*, uint16_t));
int uart_transmit(uint32_t,uint8_t *, uint16_t );
void uart_receiveRxCpltCallback();
void uart_loop();

#endif /* UART_H_ */
