package dev.devce.rocketnautics.content.orbit.universe;

import dev.devce.rocketnautics.client.PlanetColors;
import dev.devce.rocketnautics.content.orbit.universe.builder.UniverseDefinitionBuilder;
import net.minecraft.world.level.Level;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Vector3d;

public final class StandardUniverseProvider {
    private StandardUniverseProvider() {}

    public static UniverseDefinitionBuilder createSunOverworldMoon() {
        final Vector3d up = new Vector3d(0, 1, 0);
        final Vector3d down = new Vector3d(0, -1, 0);
        final double solRadius = 300_000_000D;
        final double overworldRadius = 3_000_000D; // 1 / 10th of the dimension radius
        final int overworldOrbitalYearInOverworldDays = 72 * 7; // one real-life week. Balance between a shorter time and having a large sphere of influence.
        final int overworldDaynightCycleLengthTicks = 24_000;
        final int overworldDaynightCycleLengthSeconds = 1200;
        final int lunarMonthInOverworldDays = 8;
        final double overworldDistance = solRadius * 40 / 3; // roughly based on the angular size of the sun in the overworld
        // orbit duration in seconds = 2pi * sqrt(r^3 / mu)
        // mu = r^3 * (2pi / orbit duration in seconds)^2
        // compute solar mu based on this
        double comp = overworldDistance * Math.PI / (overworldOrbitalYearInOverworldDays * overworldDaynightCycleLengthSeconds);
        double solMu = 4 * overworldDistance * comp * comp;

        return UniverseDefinition.builder()
                .cubePlanet(p -> p
                        .setFrameName("sol")
                        .setMu(solMu)
                        .setRenderDataOverride(i -> {
                            byte[] data = new byte[PlanetColors.ARRAY_SIZE];
                            for (int j = 0; j < 256; j++) {
                                for (int k = 0; k < 256; k++) {
                                    data[j + 256 * k] = PlanetColors.SUN_1;
                                }
                            }
                            return data;
                        })
                        .setRadius(solRadius)
                        .setRotationAxis(up)
                        .setTicksPerRevolution(overworldDaynightCycleLengthTicks * 32)
                        .setFixedPosition("root", Vector3D.ZERO))
                .cubePlanet(p -> p
                        .setFrameName("overworld")
                        .setAccelerationAtSurface(11)
                        .setClouds(true)
                        .setLinkedDimension(Level.OVERWORLD)
                        .setDimensionTransferHeight(20000)
                        .setRadius(overworldRadius)
                        .setCircularOrbit("sol", overworldOrbitalYearInOverworldDays * overworldDaynightCycleLengthSeconds, Vector3D.PLUS_J)
                        .setRotationAxis(down)
                        .setTicksPerRevolution(overworldDaynightCycleLengthTicks))
                .cubePlanet(p -> p
                        .setFrameName("moon")
                        .setAccelerationAtSurface(2)
                        .setRenderDataOverride(i -> {
                            byte[] data = new byte[PlanetColors.ARRAY_SIZE];
                            for (int j = 0; j < 256; j++) {
                                for (int k = 0; k < 256; k++) {
                                    data[j + 256 * k] = PlanetColors.MOON_1;
                                }
                            }
                            return data;
                        })
                        .setCircularOrbit("overworld", lunarMonthInOverworldDays * overworldDaynightCycleLengthSeconds, Vector3D.PLUS_J)
                        .radiusFromDistance(d -> (d - overworldRadius) * 3 / 40) // roughly based on the angular size of the moon in the overworld
                        .setTidalLocked())
                .cubePlanet(p -> p
                        .setFrameName("mars")
                        .setAccelerationAtSurface(3.7)
                        .setRadius(overworldRadius * 0.53) // Mars is smaller than Earth
                        .setCircularOrbit("sol", (int)(overworldOrbitalYearInOverworldDays * 1.88) * overworldDaynightCycleLengthSeconds, Vector3D.PLUS_J)
                        .setRotationAxis(down)
                        .setTicksPerRevolution(24_600) // ~24.6 hours
                        .setRenderDataOverride(i -> {
                            byte[] data = new byte[PlanetColors.ARRAY_SIZE];
                            for (int yCoord = 0; yCoord < 256; yCoord++) {
                                for (int xCoord = 0; xCoord < 256; xCoord++) {
                                    if (yCoord < 24 || yCoord > 232) {
                                        data[xCoord + 256 * yCoord] = PlanetColors.MARS_ICE;
                                    } else {
                                        double nx = xCoord / 12.0;
                                        double ny = yCoord / 12.0;
                                        double noise = Math.sin(nx) * Math.cos(ny) + 0.5 * Math.sin(nx * 2.3) * Math.cos(ny * 1.7);
                                        if (noise > 0.15) {
                                            data[xCoord + 256 * yCoord] = PlanetColors.MARS_DARK_RED;
                                        } else {
                                            data[xCoord + 256 * yCoord] = PlanetColors.MARS_RED;
                                        }
                                    }
                                }
                            }
                            return data;
                        }))
                .cubePlanet(p -> p
                        .setFrameName("gas_giant")
                        .setAccelerationAtSurface(24.8)
                        .setRadius(overworldRadius * 4.2) // Jupiter is massive!
                        .setCircularOrbit("sol", overworldOrbitalYearInOverworldDays * 4 * overworldDaynightCycleLengthSeconds, Vector3D.PLUS_J)
                        .setRotationAxis(down)
                        .setTicksPerRevolution(10_000) // Spins very quickly
                        .setRenderDataOverride(i -> {
                            byte[] data = new byte[PlanetColors.ARRAY_SIZE];
                            for (int yCoord = 0; yCoord < 256; yCoord++) {
                                for (int xCoord = 0; xCoord < 256; xCoord++) {
                                    double spotDx = (xCoord - 150) / 25.0;
                                    double spotDy = (yCoord - 140) / 12.0;
                                    if (spotDx * spotDx + spotDy * spotDy < 1.0) {
                                        data[xCoord + 256 * yCoord] = PlanetColors.GAS_RED;
                                    } else {
                                        double stripeVal = Math.sin(yCoord * 0.15) + Math.cos(yCoord * 0.05 + xCoord * 0.02) * 0.3;
                                        if (stripeVal > 0.4) {
                                            data[xCoord + 256 * yCoord] = PlanetColors.GAS_ORANGE;
                                        } else if (stripeVal < -0.4) {
                                            data[xCoord + 256 * yCoord] = PlanetColors.GAS_BROWN;
                                        } else {
                                            data[xCoord + 256 * yCoord] = PlanetColors.GAS_YELLOW;
                                        }
                                    }
                                }
                            }
                            return data;
                        }))
                .cubePlanet(p -> p
                        .setFrameName("ice_world")
                        .setAccelerationAtSurface(11.0)
                        .setRadius(overworldRadius * 1.8) // Neptune-like
                        .setCircularOrbit("sol", overworldOrbitalYearInOverworldDays * 8 * overworldDaynightCycleLengthSeconds, Vector3D.PLUS_J)
                        .setRotationAxis(down)
                        .setTicksPerRevolution(16_000)
                        .setRenderDataOverride(i -> {
                            byte[] data = new byte[PlanetColors.ARRAY_SIZE];
                            for (int yCoord = 0; yCoord < 256; yCoord++) {
                                for (int xCoord = 0; xCoord < 256; xCoord++) {
                                    double wave = Math.sin(yCoord * 0.1) * Math.cos(xCoord * 0.05);
                                    if (wave > 0.3) {
                                        data[xCoord + 256 * yCoord] = PlanetColors.ICE_CYAN;
                                    } else if (wave < -0.3) {
                                        data[xCoord + 256 * yCoord] = PlanetColors.ICE_DARK_BLUE;
                                    } else {
                                        data[xCoord + 256 * yCoord] = PlanetColors.ICE_BLUE;
                                    }
                                }
                            }
                            return data;
                        }));
    }
}
