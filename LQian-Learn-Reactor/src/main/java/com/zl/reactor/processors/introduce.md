### 1. Do I Need a Processor?
Most of the time, you should try to avoid using a Processor. They are harder to use correctly and prone to some corner cases.

If you think a Processor could be a good match for your use case, ask yourself if you have tried these two alternatives:
1.Could an operator or combination of operators fit the bill? 
(See https://projectreactor.io/docs/core/release/reference/#which-operator)
2.Could a "generator" operator work instead? (Generally, these operators are made to bridge APIs that are not reactive, 
providing a "sink" that is similar in concept to a Processor in the sense that it lets you populate the sequence with 
data or terminate it).

### 2. Safely Produce from Multiple Threads by Using the Sink Facade
Rather than directly using Reactor Processors, it is a good practice to obtain a Sink for the Processor by calling sink() once.
FluxProcessor sinks safely gate multi-threaded producers and can be used by applications that generate data from 
multiple threads concurrently. For example, a thread-safe serialized sink can be created for UnicastProcessor:
```
UnicastProcessor<Integer> processor = UnicastProcessor.create();
FluxSink<Integer> sink = processor.sink(overflowStrategy);
```
Multiple producer threads may concurrently generate data on the following serialized sink:
```
sink.next(n);
```

Overflow from next behaves in two possible ways, depending on the Processor and its configuration:
- An unbounded processor handles the overflow itself by dropping or buffering.
- A bounded processor blocks or "spins" on the IGNORE strategy or applies the overflowStrategy behavior specified for the sink.

### 3. Overview of Available Processors
Reactor Core comes with several flavors of Processor. Not all processors have the same semantics but are roughly split 
into three categories. The following list briefly describes the three kinds of processors:
- direct (DirectProcessor and UnicastProcessor): These processors can only push data through direct user action (calling
their Sink's methods directly).
- direct (DirectProcessor and UnicastProcessor): These processors can only push data through direct user action (calling
their Sink's methods directly).
- asynchronous (WorkQueueProcessor and TopicProcessor): These processors can push data obtained from multiple upstream 
Publishers. They are more robust and are backed by a RingBuffer data structure in order to deal with their multiple upstreams.

The asynchronous processors are the most complex to instantiate, with a lot of different options. Consequently, they 
expose a Builder interface. The simpler processors have static factory methods instead.

### 4. DirectProcessor
A DirectProcessor is a processor that can dispatch signals to zero to many Subscribers. It is the simplest one to 
instantiate, with a single create() static factory method. On the other hand, it has the limitation of not handling 
backpressure. As a consequence, a DirectProcessor signals an IllegalStateException to its subscribers if you push N 
elements through it but at least one of its subscribers has requested less than N.

Once the Processor has terminated (usually through its Sink error(Throwable) or complete() methods being called), it 
lets more subscribers subscribe but replays the termination signal to them immediately.

### 5. UnicastProcessor
A UnicastProcessor can deal with backpressure using an internal buffer. The trade-off is that it can have at most one Subscriber.

A UnicastProcessor has a few more options, reflected by a few create static factory methods. For instance, by default it
is unbounded: if you push any amount of data through it while its Subscriber has not yet requested data, it will buffer 
all of the data.

This can be changed by providing a custom Queue implementation for the internal buffering in the create factory method. 
If that queue is bounded, the processor could reject the push of a value when the buffer is full and not enough requests
from downstream have been received.

In that bounded case, the processor can also be built with a callback that is invoked on each rejected element, allowing
for cleanup of these rejected elements.

### 6. EmitterProcessor
An EmitterProcessor is capable of emitting to several subscribers, while honoring backpressure for each of its 
subscribers. It can also subscribe to a Publisher and relay its signals synchronously.

Initially, when it has no subscriber, it can still accept a few data pushes up to a configurable bufferSize. After that 
point, if no Subscriber has come in and consumed the data, calls to onNext block until the processor is drained (which 
can only happen concurrently by then).

Thus, the first Subscriber to subscribe receives up to bufferSize elements upon subscribing. However, after that, the 
processor stops replaying signals to additional subscribers. These subsequent subscribers instead only receive the 
signals pushed through the processor after they have subscribed. The internal buffer is still used for backpressure purposes.

By default, if all of its subscribers are cancelled (which basically means they have all un-subscribed), it will clear 
its internal buffer and stop accepting new subscribers. This can be tuned by the autoCancel parameter in the create 
static factory methods.

### 7. ReplayProcessor
A ReplayProcessor caches elements that are either pushed directly through its Sink or elements from an upstream 
Publisher and replays them to late subscribers.

It can be created in multiple configurations:
- Caching a single element (cacheLast).
- Caching a limited history (create(int)), unbounded history (create()).
- Caching time-based replay window (createTimeout(Duration)).
- Caching combination of history size and time window (createSizeOrTimeout(int, Duration)).
  
### 8. TopicProcessor
A TopicProcessor is an asynchronous processor capable of relaying elements from multiple upstream Publishers when 
created in the shared configuration (see the share(boolean) option of the builder()).

Note that the share option is mandatory if you intend to concurrently call TopicProcessor's onNext, onComplete, or 
onError methods directly or from a concurrent upstream Publisher.

Otherwise, such concurrent calls are illegal, as the processor is then fully compliant with the Reactive Streams specification.

A TopicProcessor is capable of fanning out to multiple Subscribers. It does so by associating a Thread to each Subscriber,
which will run until an onError or onComplete signal is pushed through the processor or until the associated Subscriber 
is cancelled. The maximum number of downstream subscribers is driven by the executor builder option. Provide a bounded 
ExecutorService to limit it to a specific number.

The processor is backed by a RingBuffer data structure that stores pushed signals. Each Subscriber thread keeps track of
its associated demand and the correct indexes in the RingBuffer.

This processor also has an autoCancel builder option: If set to true (the default), it results in the source Publisher(s)
being cancelled when all subscribers are cancelled.

### 9. WorkQueueProcessor
A WorkQueueProcessor is also an asynchronous processor capable of relaying elements from multiple upstream Publishers 
when created in the shared configuration (it shares most of its builder options with TopicProcessor).

It relaxes its compliance with the Reactive Streams specification, but it acquires the benefit of requiring fewer 
resources than the TopicProcessor. It is still based on a RingBuffer but avoids the overhead of creating one consumer 
Thread per Subscriber. As a result, it scales better than the TopicProcessor.

The trade-off is that its distribution pattern is a little bit different: Requests from each subscriber all add up 
together, and the processor relays signals to only one Subscriber at a time, in a kind of round-robin distribution 
rather than fan-out pattern.

A fair round-robin distribution is not guaranteed.

The WorkQueueProcessor mostly has the same builder options as the TopicProcessor, such as autoCancel, share, and 
waitStrategy. The maximum number of downstream subscribers is also driven by a configurable ExecutorService with the 
executor option.

You should take care not to subscribe too many Subscribers to a WorkQueueProcessor, as doing so could lock the processor.
If you need to limit the number of possible subscribers, prefer doing so by using a ThreadPoolExecutor or a ForkJoinPool.
The processor can detect their capacity and throw an exception if you subscribe one too many times.








