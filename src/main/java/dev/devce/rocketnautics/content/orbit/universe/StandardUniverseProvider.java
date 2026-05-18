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
        final double solRadius = 300_000_000D;
        final double overworldRadius = 3_000_000D; // 1 / 10th of the dimension radius
        final int overworldOrbitalYearInOverworldDays = 72 * 7; // one real-life week. Balance between a shorter time and having a large sphere of influence.
        final int overworldDaynightCycleLengthTicks = 24_000;
        final int overworldDaynightCycleLengthSeconds = 1200;
        final int lunarMonthInOverworldDays = 8;
        final double overworldDistance = solRadius * 40 / 3; // roughly based on the angular size of the sun in the overworld
        double earthMu = 11 * overworldRadius * overworldRadius;
        // orbit duration in seconds = 2pi * sqrt(r^3 / mu)
        // mu = r^3 * (2pi / orbit duration in seconds)^2
        // compute solar mu based on this
        double comp = overworldDistance * Math.PI / (overworldOrbitalYearInOverworldDays * overworldDaynightCycleLengthSeconds);
        double solMu = 4 * overworldDistance * comp * comp;
        // r^3 = mu * (orbit duration in seconds / 2pi)^2
        // compute moon distance based on this
        comp = lunarMonthInOverworldDays * overworldDaynightCycleLengthSeconds / Math.PI;
        double moonDistance = Math.cbrt(earthMu * comp * comp / 4);
        double moonRadius = (moonDistance - overworldRadius) * 3 / 40; // roughly based on the angular size of the moon in the overworld

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
                        .setMu(earthMu)
                        .setClouds(true)
                        .setLinkedDimension(Level.OVERWORLD)
                        .setDimensionTransferHeight(20000)
                        .setRadius(overworldRadius)
                        .setCircularOrbit("sol", Vector3D.PLUS_I.scalarMultiply(overworldDistance), Vector3D.PLUS_J)
                        .setRotationAxis(up)
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
                        .setRadius(moonRadius)
                        .setCircularOrbit("overworld", Vector3D.PLUS_I.scalarMultiply(moonDistance), Vector3D.PLUS_J)
                        .setTidalLocked());
    }
}
