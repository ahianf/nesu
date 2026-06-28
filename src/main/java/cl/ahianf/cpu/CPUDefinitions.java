package cl.ahianf.cpu;


import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CPUDefinitions {

    // =============================================================
    // BITMASKS & SHIFTS
    // =============================================================
    public static final int InstructionModeMask = 0x3;

    public static final int OperationMask = 0xe0;
    public static final int OperationShift = 5;

    public static final int AddrModeMask = 0x1c;
    public static final int AddrModeShift = 2;

    public static final int BranchInstructionMask = 0x1f;
    public static final int BranchInstructionMaskResult = 0x10;
    public static final int BranchConditionMask = 0x20;
    public static final int BranchOnFlagShift = 6;

    // =============================================================
    // VECTORS
    // =============================================================
    public static final int NMIVector = 0xfffa;
    public static final int ResetVector = 0xfffc;
    public static final int IRQVector = 0xfffe;

    // =============================================================
    // ENUMS
    // =============================================================

    public enum InterruptType {
        IRQ, NMI, BRK_
    }

    public enum BranchOnFlag {
        Negative(0), Overflow(1), Carry(2), Zero(3);

        public final int val;

        BranchOnFlag(int v) {
            this.val = v;
        }
    }

    // --- Type 1 Instructions ---
    public enum Operation1 {
        ORA(0), AND(1), EOR(2), ADC(3), STA(4), LDA(5), CMP(6), SBC(7);

        public final int val;
        private static final Operation1[] LOOKUP = values();

        Operation1(int v) {
            this.val = v;
        }

        public static Operation1 fromInt(int i) {
            return (i >= 0 && i < LOOKUP.length) ? LOOKUP[i] : ORA;
        }

    }

    public enum AddrMode1 {
        IndexedIndirectX(0), ZeroPage(1), Immediate(2), Absolute(3), IndirectY(4), IndexedX(5), AbsoluteY(6), AbsoluteX(7);

        public final int val;
        private static final AddrMode1[] LOOKUP = values();

        AddrMode1(int v) {
            this.val = v;
        }

        public static AddrMode1 fromInt(int i) {
            return (i >= 0 && i < LOOKUP.length) ? LOOKUP[i] : ZeroPage;
        }

    }

    // --- Type 2 Instructions ---
    public enum Operation2 {
        ASL(0), ROL(1), LSR(2), ROR(3), STX(4), LDX(5), DEC(6), INC(7);

        public final int val;
        private static final Operation2[] LOOKUP = values();

        Operation2(int v) {
            this.val = v;
        }

        public static Operation2 fromInt(int i) {
            return (i >= 0 && i < LOOKUP.length) ? LOOKUP[i] : ASL;
        }
    }

    public enum AddrMode2 {
        Immediate_(0), ZeroPage_(1), Accumulator(2), Absolute_(3),
        Indexed(5), // Skips 4
        AbsoluteIndexed(7); // Skips 6

        public final int val;

        // 1. Create a static array size 8 (to cover indices 0 through 7)
        private static final AddrMode2[] CACHE = new AddrMode2[8];

        static {
            // 2. Fill the cache
            // First, fill everything with a default to handle the "holes" (4 and 6) safeley
            Arrays.fill(CACHE, Immediate_);

            // Overwrite valid slots with the correct Enum
            // We iterate values() here ONCE when the class loads, so it's fine.
            for (AddrMode2 m : values()) {
                CACHE[m.val] = m;
            }
        }

        AddrMode2(int v) {
            this.val = v;
        }

        public static AddrMode2 fromInt(int i) {
            // 3. Fast lookup with a safety check
            if (i >= 0 && i < CACHE.length) {
                return CACHE[i];
            }
            return Immediate_; // Fallback for out of bounds
        }
    }

    // --- Type 0 Instructions ---
    public enum Operation0 {
        BIT(1), STY(4), LDY(5), CPY(6), CPX(7);

        private static final Operation0[] CACHE = new Operation0[8];
        public final int val;

        Operation0(int v) {
            this.val = v;
        }

        static {
            Arrays.fill(CACHE, BIT);
            for (Operation0 m : values()) {
                CACHE[m.val] = m;
            }
        }

        public static Operation0 fromInt(int i) {
            if (i >= 0 && i < CACHE.length) {
                return CACHE[i];
            }
            return BIT;
        }
    }

    // --- Implied Instructions ---
    // These have specific Opcode values rather than calculated modes
    public enum OperationImplied {
        NOP(0xea), BRK(0x00), JSR(0x20), RTI(0x40), RTS(0x60),
        JMP(0x4C), JMPI(0x6C),
        PHP(0x08), PLP(0x28), PHA(0x48), PLA(0x68),
        DEY(0x88), DEX(0xca), TAY(0xa8), INY(0xc8), INX(0xe8),
        CLC(0x18), SEC(0x38), CLI(0x58), SEI(0x78), TYA(0x98), CLV(0xb8), CLD(0xd8), SED(0xf8),
        TXA(0x8a), TXS(0x9a), TAX(0xaa), TSX(0xba);

        public final int opcode;
        private static final OperationImplied[] CACHE = new OperationImplied[256];

        static {
            for (OperationImplied op : values()) {
                CACHE[op.opcode] = op;
            }
        }

        OperationImplied(int opcode) {
            this.opcode = opcode;
        }

        public static OperationImplied fromOpcode(int opcode) {
            if (opcode >= 0 && opcode < CACHE.length) {
                return CACHE[opcode];
            }
            return null;
        }
    }

    // =============================================================
    // CYCLE LOOKUP TABLE
    // =============================================================

    // 0 implies unused opcode
    public static final int[] OperationCycles = {7, 6, 0, 0, 0, 3, 5, 0, 3, 2, 2, 0, 0, 4, 6, 0, 2, 5, 0, 0, 0, 4, 6, 0, 2, 4, 0, 0, 0, 4, 7, 0, 6, 6, 0, 0, 3, 3, 5, 0, 4, 2, 2, 0, 4, 4, 6, 0, 2, 5, 0, 0, 0, 4, 6, 0, 2, 4, 0, 0, 0, 4, 7, 0, 6, 6, 0, 0, 0, 3, 5, 0, 3, 2, 2, 0, 3, 4, 6, 0, 2, 5, 0, 0, 0, 4, 6, 0, 2, 4, 0, 0, 0, 4, 7, 0, 6, 6, 0, 0, 0, 3, 5, 0, 4, 2, 2, 0, 5, 4, 6, 0, 2, 5, 0, 0, 0, 4, 6, 0, 2, 4, 0, 0, 0, 4, 7, 0, 0, 6, 0, 0, 3, 3, 3, 0, 2, 0, 2, 0, 4, 4, 4, 0, 2, 6, 0, 0, 4, 4, 4, 0, 2, 5, 2, 0, 0, 5, 0, 0, 2, 6, 2, 0, 3, 3, 3, 0, 2, 2, 2, 0, 4, 4, 4, 0, 2, 5, 0, 0, 4, 4, 4, 0, 2, 4, 2, 0, 4, 4, 4, 0, 2, 6, 0, 0, 3, 3, 5, 0, 2, 2, 2, 0, 4, 4, 6, 0, 2, 5, 0, 0, 0, 4, 6, 0, 2, 4, 0, 0, 0, 4, 7, 0, 2, 6, 0, 0, 3, 3, 5, 0, 2, 2, 2, 2, 4, 4, 6, 0, 2, 5, 0, 0, 0, 4, 6, 0, 2, 4, 0, 0, 0, 4, 7, 0,};
}
