/**
 * Copyright 2016 Neeve Research, LLC
 *
 * This product includes software developed at Neeve Research, LLC
 * (http://www.neeveresearch.com/) as well as software licenced to
 * Neeve Research, LLC under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Neeve Research licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.neeve.toa.test.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Properties;
import java.util.Set;

import org.junit.Test;

import com.neeve.aep.AepBusManager;
import com.neeve.aep.AepEngine.HAPolicy;
import com.neeve.aep.annotations.EventHandler;
import com.neeve.lang.XString;
import com.neeve.rog.IRogMessage;
import com.neeve.server.app.annotations.AppHAPolicy;
import com.neeve.sma.MessageBusBindingFactory;
import com.neeve.sma.MessageChannel.Qos;
import com.neeve.sma.MessageChannel.RawKeyResolutionTable;
import com.neeve.sma.MessageChannelDescriptor;
import com.neeve.sma.SmaException;
import com.neeve.sma.impl.MessageChannelBase;
import com.neeve.toa.ToaException;
import com.neeve.toa.service.ToaService;
import com.neeve.toa.service.ToaServiceChannel;
import com.neeve.toa.spi.AbstractTopicResolver;
import com.neeve.toa.spi.ChannelFilterProvider;
import com.neeve.toa.spi.ChannelQosProvider;
import com.neeve.toa.spi.TopicResolver;
import com.neeve.util.UtlTailoring;

/**
 * 
 */
public class ChannelKeysAndFiltersTest extends AbstractToaTest {
    private static final Properties IKRT = new Properties();
    static {
        IKRT.put("IntField", "5");
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class SenderApp extends AbstractToaTestApp {

        public void sendTestMessage(IRogMessage message) {
            recordSend(message);
            sendMessage(message);
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static class ApplicationProvidedTopicResolverSender extends AbstractToaTestApp {

        public void sendTestMessage(int intFieldValue) {
            ReceiverMessage1 message = ReceiverMessage1.create();
            message.setIntField(intFieldValue);
            recordSend(message);
            sendMessage(message);
        }

        @Override
        public TopicResolver<?> getTopicResolver(ToaService service, ToaServiceChannel channel, Class<?> messageClass) {
            if (messageClass == ReceiverMessage1.class) {
                return new AbstractTopicResolver<ReceiverMessage1>() {
                    final XString keyBuilder = XString.create(32, true, true);

                    @Override
                    public XString resolveTopic(ReceiverMessage1 message, RawKeyResolutionTable krt) {
                        keyBuilder.clear();
                        keyBuilder.append("Receiver1/");
                        keyBuilder.append(message.getIntField());
                        return keyBuilder;
                    }

                    @Override
                    public XString resolveTopic(ReceiverMessage1 message, Properties krt) {
                        keyBuilder.clear();
                        keyBuilder.append("Receiver1/");
                        keyBuilder.append(message.getIntField());
                        return keyBuilder;
                    }
                };
            }
            return null;
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class FilteringReceiverApp extends AbstractToaTestApp {
        @EventHandler
        public void onReceiverMessage1(ReceiverMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onReceiverMessage2(ReceiverMessage2 message) {
            recordReceipt(message);
        }

        @Override
        public String getChannelFilter(ToaService service, ToaServiceChannel channel) {
            return "IntField=2|4";
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class UnfilteredReceiver extends AbstractToaTestApp {
        @EventHandler
        public void onReceiverMessage1(ReceiverMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onReceiverMessage2(ReceiverMessage2 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onReceiverMessage3(ReceiverMessage3 message) {
            recordReceipt(message);
        }

    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class ConflictingChannelFilterApp extends AbstractToaTestApp implements ChannelFilterProvider {
        @EventHandler
        public void onReceiverMessage1(ReceiverMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onReceiverMessage2(ReceiverMessage2 message) {
            recordReceipt(message);
        }

        @Override
        public void addChannelFilterProviders(Set<Object> objects) {
            objects.add(this);
            objects.add(new ChannelFilterProvider() {

                @Override
                public String getChannelFilter(ToaService service, ToaServiceChannel channel) {
                    return "IntField=1|3";
                }
            });
        }

        @Override
        public String getChannelFilter(ToaService service, ToaServiceChannel channel) {
            return "IntField=2|4";
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class CompatibleChannelFilterApp extends AbstractToaTestApp implements ChannelFilterProvider {
        @EventHandler
        public void onReceiverMessage1(ReceiverMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onReceiverMessage2(ReceiverMessage2 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onReceiverMessage3(ReceiverMessage3 message) {
            recordReceipt(message);
        }

        @Override
        public void addChannelFilterProviders(Set<Object> objects) {
            objects.add(this);
            objects.add(new ChannelFilterProvider() {

                @Override
                public String getChannelFilter(ToaService service, ToaServiceChannel channel) {
                    return "IntField=2|4";
                }
            });
        }

        @Override
        public String getChannelFilter(ToaService service, ToaServiceChannel channel) {
            return "IntField=2|4";
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class GuaranteedQosTestApp extends AbstractToaTestApp implements ChannelQosProvider {

        @EventHandler
        public void onReceiverMessage1(ReceiverMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onReceiverMessage2(ReceiverMessage2 message) {
            recordReceipt(message);
        }

        @Override
        public Qos getChannelQos(ToaService service, ToaServiceChannel channel) {
            return Qos.Guaranteed;
        }

        public void validate() {
            boolean foundBusChannels = false;
            for (AepBusManager manager : getEngine().getBusManagers()) {
                if (manager.getBusDescriptor().getName().equals(getAepEngine().getName())) {
                    for (MessageChannelDescriptor channel : manager.getBusDescriptor().getChannels()) {
                        if (channel.getChannelQos() != Qos.Guaranteed) {
                            fail("Channel: " + channel.getName() + " was not set to " + Qos.Guaranteed);
                        }
                        foundBusChannels = true;
                    }
                }
            }
            if (!foundBusChannels) {
                fail("no bus manager found matching the engine name");
            }
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class BestEfforQosTestApp extends AbstractToaTestApp implements ChannelQosProvider {
        @EventHandler
        public void onReceiverMessage1(ReceiverMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onReceiverMessage2(ReceiverMessage2 message) {
            recordReceipt(message);
        }

        @Override
        public Qos getChannelQos(ToaService service, ToaServiceChannel channel) {
            return Qos.BestEffort;
        }

        public void validate() {
            boolean foundBusChannels = false;
            for (AepBusManager manager : getEngine().getBusManagers()) {
                if (manager.getBusDescriptor().getName().equals(getAepEngine().getName())) {
                    for (MessageChannelDescriptor channel : manager.getBusDescriptor().getChannels()) {
                        if (channel.getChannelQos() != Qos.BestEffort) {
                            fail("Channel: " + channel.getName() + " was not set to " + Qos.BestEffort);
                        }
                        foundBusChannels = true;
                    }
                }
            }
            if (!foundBusChannels) {
                fail("no bus manager found matching the engine name");
            }
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class FixedKRTSenderApp extends SenderApp {

        @Override
        protected Properties getInitialChannelKeyResolutionTable(ToaService service, ToaServiceChannel channel) {
            return IKRT;
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class FixedKRTReceiverApp extends AbstractToaTestApp {

        @Override
        protected Properties getInitialChannelKeyResolutionTable(ToaService service, ToaServiceChannel channel) {
            return IKRT;
        }

        @Override
        public String getChannelFilter(ToaService service, ToaServiceChannel channel) {
            //Note this *should* be ignored since we don't currently support content filters.
            return "IntField=1";
        }

        @EventHandler
        public void onReceiverMessage1(ReceiverMessage1 message) {
            recordReceipt(message);
        }

        @EventHandler
        public void onReceiverMessage2(ReceiverMessage2 message) {
            recordReceipt(message);
        }
    }

    @AppHAPolicy(HAPolicy.EventSourcing)
    public static final class FixedKRTReceiverAppWithDefaultInChannelKey extends AbstractToaTestApp {

        @Override
        protected Properties getInitialChannelKeyResolutionTable(ToaService service, ToaServiceChannel channel) {
            return IKRT;
        }

        @Override
        public String getChannelFilter(ToaService service, ToaServiceChannel channel) {
            //Note this tests that the default in longField isn't applied 
            //during initial key resolution
            return "LongField=3";
        }

        @EventHandler
        public void onReceiverMessage4(ReceiverMessage4 message) {
            recordReceipt(message);
        }
    }

    public static class ReceiverMessage1OnReceiver1Channel extends AbstractTopicResolver<ReceiverMessage1> {
        private final String modeledKey = "Receiver1/${IntField}/${ShortField}/${NonMessageField}";

        private final XString keyBuilder = XString.create(32, true, true);

        private final boolean[] preResolved = new boolean[3];
        private final XString[] defaults = new XString[3];

        private boolean matchesServiceKey = true;
        private XString[] channelKeyParts;
        private String[][] variableKeyComponents;
        private RawKeyResolutionTable variableKeyDefaults;

        @Override
        public void initialize(ToaServiceChannel serviceChannel) {
            super.initialize(serviceChannel);
            channelKeyParts = MessageChannelBase.parseKey(channelKey.getValue());
            variableKeyComponents = MessageChannelBase.parseChannelKeyVariables(channelKeyParts);
            variableKeyDefaults = MessageChannelBase.parseChannelKeyVariableDefaults(channelKey.getValue());

            if (!modeledKey.equals(serviceChannel.getKey())) {
                matchesServiceKey = false;

            }
            else
            {
                if (variableKeyDefaults != null) {
                    defaults[0] = variableKeyDefaults.get("IntField");
                    defaults[1] = variableKeyDefaults.get("ShortField");
                    defaults[2] = variableKeyDefaults.get("NonMessageField");
                }

                Properties props = serviceChannel.getInitialKRT() != null ? serviceChannel.getInitialKRT() : new Properties();
                preResolved[0] = props.containsKey("IntField");
                preResolved[1] = props.containsKey("ShortField");
                preResolved[2] = props.containsKey("NonMessageField");
            }
        }

        /* (non-Javadoc)
         * @see com.neeve.toa.spi.TopicResolver#resolveTopic(com.neeve.sma.MessageView)
         */
        @Override
        public XString resolveTopic(ReceiverMessage1 message, RawKeyResolutionTable krt) throws Exception {
            keyBuilder.clear();

            if (matchesServiceKey) {

                //'Receiver1/'
                int pos = 0;
                if (channelKeyParts[0] != null) {
                    keyBuilder.append(channelKeyParts[0]);
                }
                pos++;

                //'${IntField}'
                if (!preResolved[0]) {
                    if (message.hasIntField()) {
                        keyBuilder.append(message.getIntField());
                    }
                    else if (defaults[0] != null) {
                        keyBuilder.append(defaults[0]);
                    }
                    else {
                        final XString krtValue = krt == null ? null : krt.get("IntField");
                        if (krtValue == null) {
                            throw new ToaException("Value for ${IntField} not in message or KRT and no default value specified!");
                        }
                        else {
                            keyBuilder.append(krtValue);
                        }
                    }
                    pos++;

                    //'/'
                    if (channelKeyParts[pos] != null) {
                        keyBuilder.append(channelKeyParts[pos]);
                    }
                    pos++;
                }

                //'${ShortField}'
                if (!preResolved[1]) {
                    if (message.hasIntField()) {
                        keyBuilder.append(String.valueOf(message.getDoubleField()));
                    }
                    else if (defaults[1] != null) {
                        keyBuilder.append(defaults[1]);
                    }
                    else {
                        final XString krtValue = krt == null ? null : krt.get("ShortField");
                        if (krtValue == null) {
                            throw new ToaException("Value for ${ShortField} not in message or KRT and no default value specified!");
                        }
                        else {
                            keyBuilder.append(krtValue);
                        }
                    }
                    pos++;

                    //'/'
                    if (channelKeyParts[pos] != null) {
                        keyBuilder.append(channelKeyParts[pos]);
                    }
                    pos++;
                }

                //'${NonMessageField}'
                if (!preResolved[2]) {
                    if (defaults[2] != null) {
                        keyBuilder.append(defaults[2]);
                    }
                    else {
                        final XString krtValue = krt == null ? null : krt.get("NonMessageField");
                        if (krtValue == null) {
                            throw new ToaException("Value for ${NonMessageField} not in message or KRT and no default value specified!");
                        }
                        else {
                            keyBuilder.append(krtValue);
                        }
                    }
                    pos++;

                    //'/'
                    if (channelKeyParts[pos] != null) {
                        keyBuilder.append(channelKeyParts[pos]);
                    }
                    pos++;
                }

            }
            else {
                MessageChannelBase.resolveMessageKey(keyBuilder, channelKeyParts, variableKeyComponents, message, null, krt, variableKeyDefaults);
            }
            return keyBuilder;
        }

        /* (non-Javadoc)
         * @see com.neeve.toa.spi.TopicResolver#resolveTopic(com.neeve.sma.MessageView)
         */
        @Override
        public XString resolveTopic(ReceiverMessage1 message, Properties krt) throws SmaException {
            keyBuilder.clear();
            if (matchesServiceKey) {

            }
            else {
                keyBuilder.append(MessageChannelBase.resolveMessageKey(channelKeyParts, variableKeyComponents, message, null, krt, variableKeyDefaults));
            }
            return keyBuilder;
        }
    }

    public static class BasicKeyResolver extends AbstractTopicResolver<ReceiverMessage1> {
        private final String modeledKey = "Receiver1/${IntField}/${ShortField}/${NonMessageField}";

        private final XString keyBuilder = XString.create(32, true, true);

        private final boolean[] preResolved = new boolean[3];
        private final XString[] defaults = new XString[3];

        private boolean matchesServiceKey = true;
        private XString[] channelKeyParts;
        private String[][] variableKeyComponents;
        private RawKeyResolutionTable variableKeyDefaults;

        @Override
        public void initialize(ToaServiceChannel serviceChannel) {
            super.initialize(serviceChannel);
            channelKeyParts = MessageChannelBase.parseKey(channelKey.getValue());
            variableKeyComponents = MessageChannelBase.parseChannelKeyVariables(channelKeyParts);
            variableKeyDefaults = MessageChannelBase.parseChannelKeyVariableDefaults(channelKey.getValue());

            if (!modeledKey.equals(serviceChannel.getKey())) {
                matchesServiceKey = false;

            }
            else
            {
                defaults[0] = variableKeyDefaults.get("IntField");
                defaults[1] = variableKeyDefaults.get("ShortField");
                defaults[2] = variableKeyDefaults.get("NonMessageField");

                Properties props = serviceChannel.getInitialKRT() != null ? serviceChannel.getInitialKRT() : new Properties();
                preResolved[0] = props.containsKey("IntField");
                preResolved[0] = props.containsKey("ShortField");
                preResolved[0] = props.containsKey("NonMessageField");
            }
        }

        /* (non-Javadoc)
         * @see com.neeve.toa.spi.TopicResolver#resolveTopic(com.neeve.sma.MessageView)
         */
        @Override
        public XString resolveTopic(ReceiverMessage1 message, RawKeyResolutionTable krt) throws Exception {
            keyBuilder.clear();

            if (matchesServiceKey) {

                //'Receiver1/'
                int pos = 0;
                if (channelKeyParts[0] != null) {
                    keyBuilder.append(channelKeyParts[0]);
                }
                pos++;

                //'${IntField}'
                if (!preResolved[0]) {
                    if (message.hasIntField()) {
                        keyBuilder.append(message.getIntField());
                    }
                    else if (defaults[0] != null) {
                        keyBuilder.append(defaults[0]);
                    }
                    else {
                        final XString krtValue = krt.get("IntField");
                        if (krtValue == null) {
                            throw new ToaException("Value for ${IntField} not in message or KRT and no default value specified!");
                        }
                        else {
                            keyBuilder.append(krtValue);
                        }
                    }
                    pos++;

                    //'/'
                    if (channelKeyParts[pos] != null) {
                        keyBuilder.append(channelKeyParts[pos]);
                    }
                    pos++;
                }

                //'${ShortField}'
                if (!preResolved[1]) {
                    if (message.hasIntField()) {
                        keyBuilder.append(String.valueOf(message.getDoubleField()));
                    }
                    else if (defaults[1] != null) {
                        keyBuilder.append(defaults[0]);
                    }
                    else {
                        final XString krtValue = krt.get("ShortField");
                        if (krtValue == null) {
                            throw new ToaException("Value for ${ShortField} not in message or KRT and no default value specified!");
                        }
                        else {
                            keyBuilder.append(krtValue);
                        }
                    }
                    pos++;

                    //'/'
                    if (channelKeyParts[pos] != null) {
                        keyBuilder.append(channelKeyParts[pos]);
                    }
                    pos++;
                }

                //'${NonMessageField}'
                if (!preResolved[1]) {
                    if (defaults[1] != null) {
                        keyBuilder.append(defaults[0]);
                    }
                    else {
                        final XString krtValue = krt.get("NonMessageField");
                        if (krtValue == null) {
                            throw new ToaException("Value for ${NonMessageField} not in message or KRT and no default value specified!");
                        }
                        else {
                            keyBuilder.append(krtValue);
                        }
                    }
                    pos++;

                    //'/'
                    if (channelKeyParts[pos] != null) {
                        keyBuilder.append(channelKeyParts[pos]);
                    }
                    pos++;
                }

            }
            else {
                MessageChannelBase.resolveMessageKey(keyBuilder, channelKeyParts, variableKeyComponents, message, null, krt, variableKeyDefaults);
            }
            return keyBuilder;
        }

        /* (non-Javadoc)
         * @see com.neeve.toa.spi.TopicResolver#resolveTopic(com.neeve.sma.MessageView)
         */
        @Override
        public XString resolveTopic(ReceiverMessage1 message, Properties krt) throws SmaException {
            keyBuilder.clear();
            if (matchesServiceKey) {

            }
            else {
                keyBuilder.append(MessageChannelBase.resolveMessageKey(channelKeyParts, variableKeyComponents, message, null, krt, variableKeyDefaults));
            }
            return keyBuilder;
        }
    }

    /**
     * Tests that dynamic portion of a channel key are left untouched if they aren't
     * in the initial KRT and that no exception is thrown. 
     */
    @Test
    public void testInitialChannelKeyResolutionUnresolvedProperties() {
        String testKey = "ORDERS/${Region}/${Product}";
        Properties initialKRT = new Properties();
        initialKRT.put("Region", "US");
        initialKRT.put("Product1", "Bar");

        assertEquals("Wrong initialized channel key", "ORDERS/US/${Product}", UtlTailoring.springScanAndReplace(testKey, initialKRT));
    }

    @Test
    public void testConflictingFilterProviders() throws Throwable {
        try {
            createApp("conlfictingFilters", "standalone", ConflictingChannelFilterApp.class);
            fail("Application with conflicting channel filters started successfully");
        }
        catch (Exception e) {
            assertTrue("Expected startup failure exception to contain 'Conflicting'", e.getMessage().toLowerCase().indexOf("conflicting channel filters") >= 0);
        }
    }

    @Test
    public void testMultipleCompatibleFilterProviders() throws Throwable {
        CompatibleChannelFilterApp receiver = createApp("compatibleFilters", "standalone", CompatibleChannelFilterApp.class);
        SenderApp sender = createApp("sender", "standalone", SenderApp.class);

        for (int i = 1; i <= 4; i++) {
            ReceiverMessage1 m = ReceiverMessage1.create();
            m.setIntField(i);
            sender.sendTestMessage(m);
        }

        sender.assertExpectedSends(5, 4);

        if (verbose()) {
            for (IRogMessage message : sender.sent) {
                System.out.println("Sent: " + message.toString());
            }
        }

        receiver.assertExpectedReceipt(5, 2);

        sender.waitForTransactionStability(2);
        receiver.waitForTransactionStability(2);

    }

    @Test
    public void testMessageKeyResolution() throws Throwable {
        UnfilteredReceiver receiver = createApp("testMessageKeyResolution", "standalone", UnfilteredReceiver.class);
        SenderApp sender = createApp("sender", "standalone", SenderApp.class);

        for (int i = 1; i <= 4; i++) {
            ReceiverMessage3 m = ReceiverMessage3.create();
            m.setIntField(i);
            m.setLongField(i);
            sender.sendTestMessage(m);
        }

        sender.assertExpectedSends(5, 4);

        if (verbose()) {
            for (IRogMessage message : sender.sent) {
                System.out.println("Sent: " + message.toString());
            }
        }

        receiver.assertExpectedReceipt(5, 4);

        sender.waitForTransactionStability(2);
        receiver.waitForTransactionStability(2);

    }

    @Test
    public final void testFilteredReceiver() throws Throwable {
        FilteringReceiverApp receiver = createApp("receiver", "standalone", FilteringReceiverApp.class);
        SenderApp sender = createApp("sender", "standalone", SenderApp.class);

        for (int i = 1; i <= 4; i++) {
            ReceiverMessage1 m = ReceiverMessage1.create();
            m.setIntField(i);
            sender.sendTestMessage(m);
        }

        sender.waitForTransactionStability(4);
        receiver.waitForTransactionStability(2);

        sender.assertExpectedSends(5, 4);

        if (verbose()) {
            for (IRogMessage message : sender.sent) {
                System.out.println("Sent: " + message.toString());
            }
        }

        receiver.assertExpectedReceipt(5, 2);

    }

    @Test
    public final void testGuaranteedQos() throws Throwable {
        GuaranteedQosTestApp receiver = createApp("receiver", "standalone", GuaranteedQosTestApp.class);
        receiver.validate();
    }

    @Test
    public final void testBestEffortQos() throws Throwable {
        BestEfforQosTestApp receiver = createApp("receiver", "standalone", BestEfforQosTestApp.class);
        receiver.validate();
    }

    @Test
    public final void testInitialKRT() throws Throwable {
        FixedKRTReceiverApp receiver = createApp("receiver", "standalone", FixedKRTReceiverApp.class);
        FixedKRTSenderApp sender = createApp("sender", "standalone", FixedKRTSenderApp.class);

        for (int i = 1; i <= 4; i++) {
            ReceiverMessage1 m = ReceiverMessage1.create();
            // note: the int field should not come into play in routing
            // because IntField is resolved to 5 by initial KRT and therefore
            // no longer dynamic.
            m.setIntField(i);
            sender.sendTestMessage(m);
        }

        sender.waitForTransactionStability(4);
        sender.assertExpectedSends(5, 4);

        receiver.waitForTransactionStability(4);

        if (verbose()) {
            for (IRogMessage message : sender.sent) {
                System.out.println("Sent: " + message.toString());
            }
        }

        receiver.assertExpectedReceipt(5, 4);
    }

    @Test
    public final void testInitialKRTDoesntSubstituteDefaults() throws Throwable {
        FixedKRTReceiverAppWithDefaultInChannelKey receiver = createApp("receiver", "standalone", FixedKRTReceiverAppWithDefaultInChannelKey.class);
        FixedKRTSenderApp sender = createApp("sender", "standalone", FixedKRTSenderApp.class);

        for (int i = 1; i <= 4; i++) {
            ReceiverMessage4 m = ReceiverMessage4.create();
            // note: the Long field in the channel key should not be 
            // substituted by the initial KRT and thus remain dynamic
            // meaning the receiver should only get message 3:
            m.setLongField(i);
            sender.sendTestMessage(m);
        }

        sender.waitForTransactionStability(4);
        sender.assertExpectedSends(5, 4);

        receiver.waitForTransactionStability(1);

        if (verbose()) {
            for (IRogMessage message : sender.sent) {
                System.out.println("Sent: " + message.toString());
            }
        }

        receiver.assertExpectedReceipt(5, 1);

        if (verbose()) {
            for (IRogMessage message : receiver.received) {
                System.out.println("Received: " + message.toString());
            }
        }
    }

    @Test
    public final void testApplicationProvidedTopicResolver() throws Throwable {
        FilteringReceiverApp receiver = createApp("receiver", "standalone", FilteringReceiverApp.class);
        ApplicationProvidedTopicResolverSender sender = createApp("sender", "standalone", ApplicationProvidedTopicResolverSender.class);

        for (int i = 1; i <= 4; i++) {
            sender.sendTestMessage(i);
        }

        sender.waitForTransactionStability(4);
        sender.assertExpectedSends(5, 4);

        receiver.waitForTransactionStability(2);

        if (verbose()) {
            for (IRogMessage message : sender.sent) {
                System.out.println("Sent: " + message.toString());
            }
        }

        receiver.assertExpectedReceipt(5, 2);
    }

    @Test
    public void testStaticKeyResolver() throws Exception {
        ReceiverMessage1OnReceiver1Channel resolver = new ReceiverMessage1OnReceiver1Channel();
        ToaService service = ToaService.unmarshal(getClass().getResource("/services/receiverService.xml"));
        ToaServiceChannel channel = null;
        for (ToaServiceChannel c : service.getChannels()) {
            if (c.getSimpleName().equals("ReceiverChannel1")) {
                channel = c;
                break;
            }
        }
        Properties initialKRT = new Properties();
        initialKRT.put("NonMessageField", "NMVALUE");
        channel.setInitialKRT(initialKRT);
        String key = "Receiver1/${IntField}/${ShortField}/${NonMessageField}";
        channel.setKey(key);
        channel.setInitiallyResolvedKey(UtlTailoring.springScanAndReplace(channel.getKey(), initialKRT));
        resolver.initialize(channel);

        RawKeyResolutionTable krt = MessageBusBindingFactory.createRawKeyResolutionTable();
        ReceiverMessage1 message = ReceiverMessage1.create();
        message.setIntField(10);
        message.setShortField((short)20);

        final int CYCLES = 100;

        final XString channelKey = XString.create(channel.getInitiallyResolvedKey(), true, true);
        final XString keyBuilder = XString.create(32, true, true);
        final XString[] channelKeyParts = MessageChannelBase.parseKey(channelKey.getValue());
        final String[][] variableKeyComponents = MessageChannelBase.parseChannelKeyVariables(channelKeyParts);
        final RawKeyResolutionTable variableKeyDefaults = MessageChannelBase.parseChannelKeyVariableDefaults(channelKey.getValue());

        long start = System.nanoTime();
        for (int i = 0; i < CYCLES; i++) {
            resolver.resolveTopic(message, krt);
        }
        long duration = System.nanoTime() - start;
        System.out.println("Static Key Resolution: " + duration + "(" + duration / CYCLES + " ns/resolution)");

        start = System.nanoTime();
        for (int i = 0; i < CYCLES; i++) {
            keyBuilder.clear();
            MessageChannelBase.resolveMessageKey(keyBuilder, channelKeyParts, variableKeyComponents, message, null, krt, variableKeyDefaults);
        }
        duration = System.nanoTime() - start;
        System.out.println("Dynamic Key Resolution: " + duration + "(" + duration / CYCLES + " ns/resolution)");

        start = System.nanoTime();
        for (int i = 0; i < CYCLES; i++) {
            resolver.resolveTopic(message, krt);
        }
        duration = System.nanoTime() - start;
        System.out.println("Static Key Resolution: " + duration + "(" + duration / CYCLES + " ns/resolution)");

        start = System.nanoTime();
        for (int i = 0; i < CYCLES; i++) {
            keyBuilder.clear();
            MessageChannelBase.resolveMessageKey(keyBuilder, channelKeyParts, variableKeyComponents, message, null, krt, variableKeyDefaults);
        }
        duration = System.nanoTime() - start;
        System.out.println("Dynamic Key Resolution: " + duration + "(" + duration / CYCLES + " ns/resolution)");
    }
}
