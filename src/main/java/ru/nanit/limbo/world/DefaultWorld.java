package ru.nanit.limbo.world;

import net.kyori.adventure.nbt.CompoundBinaryTag;

public final class DefaultWorld {

    private static CompoundBinaryTag heightMaps;

    private DefaultWorld(){}

    public static void init(){
        heightMaps = CompoundBinaryTag.builder()
                .putLongArray("MOTION_BLOCKING", new long[]{
                        1371773531765642314L, 1389823183635651148L, 1371738278539598925L,
                        1389823183635388492L, 1353688558756731469L, 1389823114781694027L, 1317765589597723213L,
                        1371773531899860042L, 1389823183635651149L, 1371773462911685197L, 1389823183635650636L,
                        1353688626805119565L, 1371773531900123211L, 1335639250618849869L, 1371738278674077258L,
                        1389823114781694028L, 1353723811310638154L, 1371738278674077259L, 1335674228429068364L,
                        1335674228429067338L, 1335674228698027594L, 1317624576693539402L, 1335709481520370249L,
                        1299610178184057417L, 1335638906349064264L, 1299574993811968586L, 1299574924958011464L,
                        1299610178184056904L, 1299574924958011464L, 1299610109330100296L, 1299574924958011464L,
                        1299574924823793736L, 1299574924958011465L, 1281525273222484040L, 1299574924958011464L,
                        1281525273222484040L, 9548107335L
                })
                .build();
    }

    public static CompoundBinaryTag getHeightMaps(){
        return heightMaps;
    }

}