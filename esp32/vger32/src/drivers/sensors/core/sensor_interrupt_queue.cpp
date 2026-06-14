/*
 * sensor_interrupt_queue.cpp
 *
 * Responsibility: ISR-safe circular queue that bridges interrupt context and
 *                 main loop. ISRs push events via sensor_iqueue_push(); the
 *                 main loop drains them via sensor_iqueue_process().
 */

#include "sensor_interrupt_queue.h"

// ==========================================
// QUEUE EVENT
// ==========================================
typedef struct {
    iqueue_callback_t callback;
    float value;
    uint32_t timestamp;
} IrqEvent_t;

// ==========================================
// PRIVATE STATE
// head: ISR writes here
// tail: main loop reads here
// ==========================================
static volatile IrqEvent_t queue[SENSOR_IQUEUE_SIZE];
static volatile uint8_t head = 0;
static volatile uint8_t tail = 0;

// ==========================================
// PUBLIC API
// ==========================================
void sensor_iqueue_push(iqueue_callback_t callback, float value, uint32_t timestamp) {
    uint8_t next_head = (head + 1) % SENSOR_IQUEUE_SIZE;

    // Queue full — drop event (overflow protection)
    if (next_head == tail) return;

    queue[head].callback = callback;
    queue[head].value = value;
    queue[head].timestamp = timestamp;
    head = next_head;
}

void sensor_iqueue_process() {
    while (tail != head) {
        iqueue_callback_t callback = queue[tail].callback;
        float value = queue[tail].value;
        uint32_t timestamp = queue[tail].timestamp;

        tail = (tail + 1) % SENSOR_IQUEUE_SIZE;

        callback(value, timestamp);
    }
}