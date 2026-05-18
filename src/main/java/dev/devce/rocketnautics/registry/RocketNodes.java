package dev.devce.rocketnautics.registry;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.blocks.SputnikBlockEntity;
import dev.devce.rocketnautics.api.peripherals.IPeripheral;
import dev.devce.websnodelib.api.NodeRegistry;
import dev.devce.websnodelib.api.WNode;
import dev.devce.websnodelib.api.WGraph;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.luaj.vm2.lib.*;

public class RocketNodes {
    public static void register() {
        // --- Sensors ---
        
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "altitude"), "Sensors", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "altitude"), "Altitude", x, y);
            node.addOutput("m", 0xFF00AAFF);
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    n.getOutputs().get(0).setValue(sputnik.getAltitude());
                }
            });
            return node;
        });

        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "velocity"), "Sensors", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "velocity"), "Velocity", x, y);
            node.addOutput("m/s", 0xFF00AAFF);
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    n.getOutputs().get(0).setValue(sputnik.getVelocity());
                }
            });
            return node;
        });

        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "attitude"), "Sensors", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "attitude"), "Attitude", x, y);
            node.addOutput("Pitch", 0xFFFFAA00);
            node.addOutput("Yaw", 0xFFFFAA00);
            node.addOutput("Roll", 0xFFFFAA00);
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    n.getOutputs().get(0).setValue(sputnik.getPitch());
                    n.getOutputs().get(1).setValue(sputnik.getYaw());
                    n.getOutputs().get(2).setValue(sputnik.getRoll());
                }
            });
            return node;
        });

        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "attitude_display"), "Display", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "attitude_display"), "Attitude Display", x, y);
            
            // Add inputs for debugging/wiring
            node.addInput("Pitch", 0xFFFFAA00);
            node.addInput("Yaw", 0xFFFFAA00);
            node.addInput("Roll", 0xFFFFAA00);
            
            // Full-width viewport
            dev.devce.websnodelib.api.elements.WViewport3D viewport = new dev.devce.websnodelib.api.elements.WViewport3D(120, 120);
            viewport.setZoom(0.8f);
            
            // Tighten the Y spacing to 0.5f since GUI item rendering scales them down by 50%
            // Rotate the thruster 180 degrees on X so the nozzle points down
            viewport.addModel(new net.minecraft.world.item.ItemStack(RocketBlocks.ROCKET_THRUSTER.get()), new org.joml.Vector3f(0, -0.75f, 0), new org.joml.Vector3f(180, 0, 0), 1.0f);
            viewport.addModel(new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.IRON_BLOCK), new org.joml.Vector3f(0, -0.25f, 0), new org.joml.Vector3f(0, 0, 0), 1.0f);
            viewport.addModel(new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.IRON_BLOCK), new org.joml.Vector3f(0, 0.25f, 0), new org.joml.Vector3f(0, 0, 0), 1.0f);
            viewport.addModel(new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.LIGHTNING_ROD), new org.joml.Vector3f(0, 0.75f, 0), new org.joml.Vector3f(0, 0, 0), 1.0f);
            
            node.addElement(viewport);
            
            node.setEvaluator(n -> {
                // Read from pins instead of directly from the context
                float pitch = (float) n.getInputs().get(0).getValue();
                float yaw = (float) n.getInputs().get(1).getValue();
                float roll = (float) n.getInputs().get(2).getValue();
                viewport.setGlobalRotation(pitch, yaw, roll);
            });
            return node;
        });

        // --- Actuators ---

        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "peripheral_list"), "System", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "peripheral_list"), "Peripheral List", x, y);
            
            // Custom element to draw the list
            dev.devce.websnodelib.api.WElement listElement = new dev.devce.websnodelib.api.WElement() {
                private java.util.List<String> lines = new java.util.ArrayList<>();
                
                {
                    this.width = 160;
                    this.height = 16;
                }
                
                public void render(net.minecraft.client.gui.GuiGraphics graphics, int ex, int ey, int mouseX, int mouseY, float partialTick) {
                    if (lines.isEmpty()) {
                        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "No engines found.", ex + 4, ey + 4, 0xFF888888, false);
                    } else {
                        for (int i = 0; i < lines.size(); i++) {
                            graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, lines.get(i), ex + 4, ey + 4 + i * 12, 0xFF00FF88, false);
                        }
                    }
                }

                public void update(java.util.List<String> newLines) {
                    this.lines = newLines;
                    this.height = lines.isEmpty() ? 16 : lines.size() * 12 + 8;
                }
            };
            
            node.addElement(listElement);
            
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    java.util.List<String> current = new java.util.ArrayList<>();
                    java.util.List<dev.devce.rocketnautics.api.peripherals.IPeripheral> periphs = sputnik.getPeripherals();
                    for (int i = 0; i < periphs.size(); i++) {
                        dev.devce.rocketnautics.api.peripherals.IPeripheral p = periphs.get(i);
                        if (p != null) {
                            current.add("- " + p.getPeripheralType().toUpperCase() + " [" + i + "]");
                        }
                    }
                    // Update the element
                    try {
                        for (java.lang.reflect.Method m : n.getElements().get(0).getClass().getDeclaredMethods()) {
                            if (m.getName().equals("update")) {
                                m.setAccessible(true);
                                m.invoke(n.getElements().get(0), current);
                            }
                        }
                        n.updateLayout();
                    } catch (Exception e) {}
                }
            });
            return node;
        });

        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "thruster_control"), "Actuators", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "thruster_control"), "Rocket Thruster Control", x, y);
            node.addInput("ID", 0xFF888888);
            node.addInput("Power", 0xFFFF5555);
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    int id = (int) n.getInputs().get(0).getValue();
                    double power = n.getInputs().get(1).getValue();
                    var periphs = sputnik.getPeripherals();
                    if (id >= 0 && id < periphs.size()) {
                        var p = periphs.get(id);
                        if (p != null && p.getPeripheralType().equals("thruster")) {
                            p.writeValue("thrust", power);
                        }
                    }
                }
            });
            return node;
        });

        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "vector_control"), "Actuators", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "vector_control"), "Vector Control", x, y);
            node.addInput("ID", 0xFF888888);
            node.addInput("Pitch", 0xFFFFAA00);
            node.addInput("Yaw", 0xFFFFAA00);
            node.addInput("Power", 0xFFFF5555);
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    int id = (int) n.getInputs().get(0).getValue();
                    double pitch = n.getInputs().get(1).getValue();
                    double yaw = n.getInputs().get(2).getValue();
                    double power = n.getInputs().get(3).getValue();
                    var periphs = sputnik.getPeripherals();
                    if (id >= 0 && id < periphs.size()) {
                        var p = periphs.get(id);
                        if (p != null && p.getPeripheralType().equals("vector_engine")) {
                            p.writeValues("gimbal", pitch, yaw);
                            p.writeValue("thrust", power);
                        }
                    }
                }
            });
            return node;
        });

        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rcs_control"), "Actuators", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rcs_control"), "RCS Control", x, y);
            node.addInput("ID", 0xFF888888);
            node.addInput("Power", 0xFFFF5555);
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    int id = (int) n.getInputs().get(0).getValue();
                    double power = n.getInputs().get(1).getValue();
                    var periphs = sputnik.getPeripherals();
                    if (id >= 0 && id < periphs.size()) {
                        var p = periphs.get(id);
                        if (p != null && p.getPeripheralType().equals("rcs")) {
                            p.writeValue("thrust", power);
                        }
                    }
                }
            });
            return node;
        });

        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "booster_control"), "Actuators", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "booster_control"), "Booster Control", x, y);
            node.addInput("ID", 0xFF888888);
            node.addInput("Power", 0xFFFF5555);
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    int id = (int) n.getInputs().get(0).getValue();
                    double power = n.getInputs().get(1).getValue();
                    var periphs = sputnik.getPeripherals();
                    if (id >= 0 && id < periphs.size()) {
                        var p = periphs.get(id);
                        if (p != null && p.getPeripheralType().equals("booster")) {
                            p.writeValue("thrust", power);
                        }
                    }
                }
            });
            return node;
        });

        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "redstone_out"), "I/O", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "redstone_out"), "Redstone Out", x, y);
            node.addInput("Level", 0xFFFF0000);
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    int level = (int) net.minecraft.util.Mth.clamp(n.getInputs().get(0).getValue(), 0, 15);
                    sputnik.setOutput("all", level);
                }
            });
            return node;
        });

        // --- Create Integration: Linked Redstone ---

        // Transmitter
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "redstone_transmitter"), "I/O", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "redstone_transmitter"), "Linked Transmitter", x, y);
            node.addInput("Signal", 0xFFFF5555);
            
            dev.devce.websnodelib.api.elements.WItemPicker freq1 = new dev.devce.websnodelib.api.elements.WItemPicker();
            dev.devce.websnodelib.api.elements.WItemPicker freq2 = new dev.devce.websnodelib.api.elements.WItemPicker();
            freq1.setBorderColor(0xFFFF3333); // Red
            freq2.setBorderColor(0xFF3333FF); // Blue
            
            node.addElement(new dev.devce.websnodelib.api.elements.WLabel("Freq 1 (Red):"));
            node.addElement(freq1);
            node.addElement(new dev.devce.websnodelib.api.elements.WLabel("Freq 2 (Blue):"));
            node.addElement(freq2);
            
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    if (freq1.getStack().isEmpty() || freq2.getStack().isEmpty()) return;
                    
                    double signal = n.getInputs().get(0).getValue();
                    dev.devce.rocketnautics.content.blocks.LinkedSignalHandler.setSignal(
                        sputnik.getLevel(), 
                        freq1.getStack(), 
                        freq2.getStack(), 
                        sputnik.getBlockPos(), 
                        signal
                    );
                }
            });
            return node;
        });

        // Receiver
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "redstone_receiver"), "I/O", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "redstone_receiver"), "Linked Receiver", x, y);
            node.addOutput("Signal", 0xFFFFAA00);
            
            dev.devce.websnodelib.api.elements.WItemPicker freq1 = new dev.devce.websnodelib.api.elements.WItemPicker();
            dev.devce.websnodelib.api.elements.WItemPicker freq2 = new dev.devce.websnodelib.api.elements.WItemPicker();
            freq1.setBorderColor(0xFFFF3333); // Red
            freq2.setBorderColor(0xFF3333FF); // Blue
            
            node.addElement(new dev.devce.websnodelib.api.elements.WLabel("Freq 1 (Red):"));
            node.addElement(freq1);
            node.addElement(new dev.devce.websnodelib.api.elements.WLabel("Freq 2 (Blue):"));
            node.addElement(freq2);
            
            node.setEvaluator(n -> {
                if (n.getParentGraph().getContext() instanceof SputnikBlockEntity sputnik) {
                    if (freq1.getStack().isEmpty() || freq2.getStack().isEmpty()) {
                        n.getOutputs().get(0).setValue(0);
                        return;
                    }
                    
                    double signal = dev.devce.rocketnautics.content.blocks.LinkedSignalHandler.getSignal(
                        sputnik.getLevel(), 
                        freq1.getStack(), 
                        freq2.getStack(), 
                        sputnik.getBlockPos()
                    );
                    n.getOutputs().get(0).setValue(signal);
                }
            });
            return node;
        });
        // --- MATH & LOGIC NODES (Turing Complete Set) ---

        // Constant Value
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "constant"), "Math", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "constant"), "Constant", x, y);
            node.addOutput("Value", 0xFFFFFFFF);
            dev.devce.websnodelib.api.elements.WTextField field = new dev.devce.websnodelib.api.elements.WTextField(40);
            field.setValue("0");
            node.addElement(field);
            node.setEvaluator(n -> {
                try {
                    n.getOutputs().get(0).setValue(Double.parseDouble(field.getValue()));
                } catch (Exception e) {
                    n.getOutputs().get(0).setValue(0);
                }
            });
            return node;
        });

        // Math Operations (Add, Sub, Mul, Div)
        registerMathNode("add", "Add", "Math", Double::sum);
        registerMathNode("sub", "Subtract", "Math", (a, b) -> a - b);
        registerMathNode("mul", "Multiply", "Math", (a, b) -> a * b);
        registerMathNode("div", "Divide", "Math", (a, b) -> b != 0 ? a / b : 0);
        registerMathNode("min", "Min", "Math", Math::min);
        registerMathNode("max", "Max", "Math", Math::max);
        registerMathNode("pow", "Power", "Math", Math::pow);

        // Unary Math (Sin, Cos, Abs, etc.)
        registerUnaryMathNode("sin", "Sine", "Math", Math::sin);
        registerUnaryMathNode("cos", "Cosine", "Math", Math::cos);
        registerUnaryMathNode("tan", "Tangent", "Math", Math::tan);
        registerUnaryMathNode("abs", "Absolute", "Math", Math::abs);
        registerUnaryMathNode("sqrt", "Square Root", "Math", Math::sqrt);
        registerUnaryMathNode("floor", "Floor", "Math", Math::floor);
        registerUnaryMathNode("ceil", "Ceiling", "Math", Math::ceil);
        registerUnaryMathNode("round", "Round", "Math", (a) -> (double) Math.round(a));

        // Comparison (Greater, Less, Equal)
        registerMathNode("greater", "Greater Than", "Logic", (a, b) -> a > b ? 1.0 : 0.0);
        registerMathNode("less", "Less Than", "Logic", (a, b) -> a < b ? 1.0 : 0.0);
        registerMathNode("equal", "Equal", "Logic", (a, b) -> Math.abs(a - b) < 0.0001 ? 1.0 : 0.0);

        // Logic (AND, OR, XOR)
        registerMathNode("and", "Logic AND", "Logic", (a, b) -> (a > 0 && b > 0) ? 1.0 : 0.0);
        registerMathNode("or", "Logic OR", "Logic", (a, b) -> (a > 0 || b > 0) ? 1.0 : 0.0);
        registerMathNode("xor", "Logic XOR", "Logic", (a, b) -> (a > 0 ^ b > 0) ? 1.0 : 0.0);

        // NOT
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "not"), "Logic", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "not"), "Logic NOT", x, y);
            node.addInput("In", 0xFFFFFFFF);
            node.addOutput("Out", 0xFFFFFFFF);
            node.setEvaluator(n -> n.getOutputs().get(0).setValue(n.getInputs().get(0).getValue() > 0 ? 0.0 : 1.0));
            return node;
        });

        // Select (If/Else)
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "select"), "Logic", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "select"), "Select", x, y);
            node.addInput("Condition", 0xFFFFFF00); // Yellow for condition
            node.addInput("If True", 0xFFFFFFFF);
            node.addInput("If False", 0xFFFFFFFF);
            node.addOutput("Result", 0xFFFFFFFF);
            node.setEvaluator(n -> {
                double cond = n.getInputs().get(0).getValue();
                double valTrue = n.getInputs().get(1).getValue();
                double valFalse = n.getInputs().get(2).getValue();
                n.getOutputs().get(0).setValue(cond > 0 ? valTrue : valFalse);
            });
            return node;
        });

        // Display Node
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "display"), "Display", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "display"), "Display", x, y);
            node.addInput("In", 0xFFFFFFFF);
            dev.devce.websnodelib.api.elements.WLabel label = new dev.devce.websnodelib.api.elements.WLabel("0.00", 0xFF00FF00); // Green text
            node.addElement(label);
            node.setEvaluator(n -> {
                double val = n.getInputs().get(0).getValue();
                label.setText(String.format("%.2f", val));
            });
            return node;
        });

        // Input Port (for functions)
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "input"), "Functions", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "input"), "Input Port", x, y);
            node.addOutput("Out", 0xFFFFFFFF);
            dev.devce.websnodelib.api.elements.WTextField nameField = new dev.devce.websnodelib.api.elements.WTextField(60);
            nameField.setValue("in_1");
            node.addElement(nameField);
            return node;
        });

        // Output Port (for functions)
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "output"), "Functions", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "output"), "Output Port", x, y);
            node.addInput("In", 0xFFFFFFFF);
            dev.devce.websnodelib.api.elements.WTextField nameField = new dev.devce.websnodelib.api.elements.WTextField(60);
            nameField.setValue("out_1");
            node.addElement(nameField);
            return node;
        });

        // The "Brain" of the Zip feature: The Function Node
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "function"), "Functions", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "function"), "Function", x, y);
            node.setInternalGraph(new WGraph());
            
            node.setEvaluator(n -> {
                WGraph sub = n.getInternalGraph();
                if (sub == null) return;

                // 1. Sync Pins (Dynamic creation)
                syncFunctionPins(n);

                // 2. Push inputs to internal input ports
                List<WNode> internalNodes = sub.getNodes();
                int inIdx = 0;
                for (WNode inNode : internalNodes) {
                    if (inNode.getTypeId().getPath().equals("input")) {
                        if (inIdx < n.getInputs().size()) {
                            double val = n.getInputs().get(inIdx).getValue();
                            inNode.getOutputs().get(0).setValue(val);
                        }
                        inIdx++;
                    }
                }

                // 3. Tick sub-graph
                sub.tick();

                // 4. Pull outputs from internal output ports
                int outIdx = 0;
                for (WNode outNode : internalNodes) {
                    if (outNode.getTypeId().getPath().equals("output")) {
                        if (outIdx < n.getOutputs().size()) {
                            double val = outNode.getInputs().get(0).getValue();
                            n.getOutputs().get(outIdx).setValue(val);
                        }
                        outIdx++;
                    }
                }
            });
            return node;
        });

        // Frame / Comment Node
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "frame"), "System", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "frame"), "Comment Frame", x, y);
            node.setWidth(200);
            node.setHeight(150);
            return node;
        });

        // --- THE SNAKE EATER: Lua Script Node ---
        // Compact node — double-click opens WLuaEditorScreen for code editing.
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "lua_script"), "Programming", (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "lua_script"), "Lua Script", x, y);

            final Globals[]  globals  = {null};
            final LuaValue[] chunk    = {null};
            final String[]   lastCode = {null};

            node.setEvaluator(n -> {
                String code = n.getCustomData().getString("code");
                if (code.isEmpty()) {
                    code = dev.devce.websnodelib.client.ui.WLuaEditorScreen.NODE_CODE_MAP.get(n.getId());
                }
                
                if (code == null || code.isBlank()) return;

                // Recompile Lua if code changed or not yet initialized in this session
                if (!code.equals(lastCode[0]) || globals[0] == null) {
                    n.getCustomData().putBoolean("failed", false);
                    
                    // Always re-parse pins to stay in sync
                    n.clearInputs();
                    n.clearOutputs();
                    Pattern pinPat = Pattern.compile("(input|output)\\(\\s*[\"']([^\"']+)[\"']");
                    for (String line : code.split("\n")) {
                        String codeOnly = line;
                        int idx = line.indexOf("--");
                        if (idx != -1) codeOnly = line.substring(0, idx);
                        Matcher m = pinPat.matcher(codeOnly);
                        while (m.find()) {
                            String kind = m.group(1), name = m.group(2);
                            if (kind.equals("input")) n.addInput(name, 0xFFFFFFFF);
                            if (kind.equals("output")) n.addOutput(name, 0xFF00FF88);
                        }
                    }

                    globals[0] = JsePlatform.standardGlobals();
                    globals[0].set("os",      LuaValue.NIL);
                    globals[0].set("io",      LuaValue.NIL);
                    globals[0].set("luajava", LuaValue.NIL);
                    globals[0].set("debug",   LuaValue.NIL);
                    globals[0].set("require", LuaValue.NIL);
                    try {
                        chunk[0]    = globals[0].load(code);
                        lastCode[0] = code;
                        n.getCustomData().putBoolean("err", false);
                    } catch (Throwable e) {
                        n.getCustomData().putBoolean("err", true);
                        chunk[0] = null;
                        return;
                    }
                }
                if (chunk[0] == null) {
                    n.getCustomData().putBoolean("err", true);
                    return;
                }

                // Bridge: input(name) → read static pin value
                globals[0].set("input", new OneArgFunction() {
                    @Override public LuaValue call(LuaValue arg) {
                        String name = arg.tojstring();
                        for (int i = 0; i < n.getInputs().size(); i++)
                            if (n.getInputs().get(i).getName().equals(name))
                                return LuaValue.valueOf(n.getInputs().get(i).getValue());
                        return LuaValue.ZERO;
                    }
                });

                // Bridge: output(name, value) → write static pin value
                globals[0].set("output", new TwoArgFunction() {
                    @Override public LuaValue call(LuaValue arg1, LuaValue arg2) {
                        String name  = arg1.tojstring();
                        double value = arg2.todouble();
                        for (int i = 0; i < n.getOutputs().size(); i++)
                            if (n.getOutputs().get(i).getName().equals(name)) {
                                n.getOutputs().get(i).setValue(value);
                                return LuaValue.NIL;
                            }
                        return LuaValue.NIL;
                    }
                });

                try {
                    chunk[0].call();
                    n.getCustomData().putBoolean("err", false);
                } catch (Throwable e) {
                    n.getCustomData().putBoolean("err", true);
                    dev.devce.websnodelib.client.ui.WLuaEditorScreen.NODE_COMPILED_MAP.put(n.getId(), false);
                }
            });

            return node;
        });
    }

    private static void syncFunctionPins(WNode node) {
        WGraph sub = node.getInternalGraph();
        if (sub == null) return;

        List<WNode> inputs = sub.getNodes().stream().filter(n -> n.getTypeId().getPath().equals("input")).toList();
        List<WNode> outputs = sub.getNodes().stream().filter(n -> n.getTypeId().getPath().equals("output")).toList();

        // Check if we need to rebuild pins
        if (node.getInputs().size() != inputs.size() || node.getOutputs().size() != outputs.size()) {
            node.clearInputs();
            for (WNode in : inputs) {
                String name = "in";
                try { name = ((dev.devce.websnodelib.api.elements.WTextField)in.getElements().get(0)).getValue(); } catch(Exception e){}
                node.addInput(name, 0xFFFFFFFF);
            }

            node.clearOutputs();
            for (WNode out : outputs) {
                String name = "out";
                try { name = ((dev.devce.websnodelib.api.elements.WTextField)out.getElements().get(0)).getValue(); } catch(Exception e){}
                node.addOutput(name, 0xFFFFFFFF);
            }
        }
    }

    private static void registerMathNode(String id, String name, String category, java.util.function.BiFunction<Double, Double, Double> op) {
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, id), category, (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, id), name, x, y);
            node.addInput("A", 0xFFFFFFFF);
            node.addInput("B", 0xFFFFFFFF);
            node.addOutput("Out", 0xFFFFFFFF);
            node.setEvaluator(n -> {
                double a = n.getInputs().get(0).getValue();
                double b = n.getInputs().get(1).getValue();
                n.getOutputs().get(0).setValue(op.apply(a, b));
            });
            return node;
        });
    }

    private static void registerUnaryMathNode(String id, String name, String category, java.util.function.Function<Double, Double> op) {
        NodeRegistry.register(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, id), category, (x, y) -> {
            WNode node = new WNode(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, id), name, x, y);
            node.addInput("In", 0xFFFFFFFF);
            node.addOutput("Out", 0xFFFFFFFF);
            node.setEvaluator(n -> {
                double val = n.getInputs().get(0).getValue();
                n.getOutputs().get(0).setValue(op.apply(val));
            });
            return node;
        });
    }
}
