################################################################################
# Automatically-generated file. Do not edit!
# Toolchain: GNU Tools for STM32 (10.3-2021.10)
################################################################################

# Add inputs and outputs from these tool invocations to the build variables 
C_SRCS += \
../user/pn_serial_protocol.c \
../user/pn_serial_protocol_test.c 

OBJS += \
./user/pn_serial_protocol.o \
./user/pn_serial_protocol_test.o 

C_DEPS += \
./user/pn_serial_protocol.d \
./user/pn_serial_protocol_test.d 


# Each subdirectory must supply rules for building sources it contributes
user/%.o user/%.su user/%.cyclo: ../user/%.c user/subdir.mk
	arm-none-eabi-gcc "$<" -mcpu=cortex-m3 -std=gnu11 -g3 -DDEBUG -DUSE_HAL_DRIVER -DSTM32F103xB -c -I../Core/Inc -I"C:/Users/NIRUJA/Desktop/Github/pn_serial_protocol_uart/pn_serial_protocol_DMA/user" -I../Drivers/STM32F1xx_HAL_Driver/Inc/Legacy -I../Drivers/STM32F1xx_HAL_Driver/Inc -I../Drivers/CMSIS/Device/ST/STM32F1xx/Include -I../Drivers/CMSIS/Include -O0 -ffunction-sections -fdata-sections -Wall -fstack-usage -fcyclomatic-complexity -MMD -MP -MF"$(@:%.o=%.d)" -MT"$@" --specs=nano.specs -mfloat-abi=soft -mthumb -o "$@"

clean: clean-user

clean-user:
	-$(RM) ./user/pn_serial_protocol.cyclo ./user/pn_serial_protocol.d ./user/pn_serial_protocol.o ./user/pn_serial_protocol.su ./user/pn_serial_protocol_test.cyclo ./user/pn_serial_protocol_test.d ./user/pn_serial_protocol_test.o ./user/pn_serial_protocol_test.su

.PHONY: clean-user

