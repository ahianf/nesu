package cl.ahianf.cpu;

import cl.ahianf.utils.IRQHandle;
import cl.ahianf.memory.MainBus;

import java.util.ArrayList;
import java.util.List;

import static cl.ahianf.cpu.CPUDefinitions.*;

public class CPU {

    // =============================================================
    // INNER CLASSES (IRQ Handler)
    // =============================================================
    public class IRQHandlerImpl implements IRQHandle {
        private final int bit;

        public IRQHandlerImpl(int bit) {
            this.bit = bit;
        }

        @Override
        public void release() {
            setIRQPulldown(bit, false);
        }

        @Override
        public void pull() {
            setIRQPulldown(bit, true);
        }
    }

    // =============================================================
    // MEMBERS
    // =============================================================

    // Registers (Using int for convenience, cast to byte on storage)
    private int r_PC; // Program Counter (16-bit)
    private int r_SP; // Stack Pointer (8-bit)
    private int r_A;  // Accumulator (8-bit)
    private int r_X;  // X Index (8-bit)
    private int r_Y;  // Y Index (8-bit)

    // Status Flags
    private boolean f_C, f_Z, f_I, f_D, f_V, f_N;

    // Timing & Interrupts
    private int m_skipCycles;
    private long m_cycles; // Use long to prevent rollover for a long time
    private boolean m_pendingNMI;
    private int m_irqPulldowns = 0;

    private MainBus m_bus;
    private final List<IRQHandlerImpl> m_irqHandlers;

    // =============================================================
    // CONSTRUCTOR
    // =============================================================
    public CPU(MainBus mem) {
        this.m_bus = mem; // Can be null initially
        this.m_irqHandlers = new ArrayList<>();
        this.m_pendingNMI = false;
    }

    // =============================================================
    // PUBLIC API
    // =============================================================

    public void reset() {
        reset(readAddress(ResetVector));
    }

    public void reset(int start_addr) {
        m_skipCycles = 0;
        m_cycles = 0;
        r_A = r_X = r_Y = 0;
        f_I = true;
        f_C = f_D = f_N = f_V = f_Z = false;
        r_PC = start_addr;
        r_SP = 0xfd; // documented startup state
    }

    public void step() {
        m_cycles++;

        if (m_skipCycles-- > 1) return;
        m_skipCycles = 0;

        if (m_pendingNMI) {
            interruptSequence(CPUDefinitions.InterruptType.NMI);
            m_pendingNMI = false;
            return;
        } else if (isPendingIRQ()) {
            interruptSequence(CPUDefinitions.InterruptType.IRQ);
            return;
        }

        // Fetch Opcode (Convert signed byte to unsigned int)
        int opcode = m_bus.read(r_PC++) & 0xFF;
        int cycleLength = OperationCycles[opcode];

        // LOGGING (simplified)
        // logStep(opcode);

        // Execute
        boolean executed = executeImplied(opcode) || executeBranch(opcode) || executeType1(opcode) || executeType2(opcode) || executeType0(opcode);

        if (cycleLength > 0 && executed) {
            m_skipCycles += cycleLength;
        } else {
            System.out.println("[ERROR] Unrecognized opcode: " + Integer.toHexString(opcode));
        }
    }

    public void nmiInterrupt() {
        m_pendingNMI = true;
    }

    public IRQHandle createIRQHandler() {
        int bit = 1 << m_irqHandlers.size();
        IRQHandlerImpl handler = new IRQHandlerImpl(bit);
        m_irqHandlers.add(handler);
        return handler;
    }

    public void setIRQPulldown(int bit, boolean state) {
        if (state) {
            m_irqPulldowns |= bit;
        } else {
            m_irqPulldowns &= ~bit;
        }
    }

    public void skipOAMDMACycles() {
        m_skipCycles += 513;
        m_skipCycles += (m_cycles & 1);
    }

    public void skipDMCDMACycles() {
        m_skipCycles += 3;
    }

    public int getPC() {
        return r_PC;
    }

    // Allows injecting Bus after construction to solve circular dependency with APU
    public void connectBus(MainBus bus) {
        this.m_bus = bus;
    }
    // =============================================================
    // PRIVATE HELPERS
    // =============================================================

    private boolean isPendingIRQ() {
        return !f_I && m_irqPulldowns != 0;
    }

    private void interruptSequence(CPUDefinitions.InterruptType type) {
        if (f_I && type != CPUDefinitions.InterruptType.NMI && type != CPUDefinitions.InterruptType.BRK_) return;

        if (type == InterruptType.BRK_) r_PC++;

        pushStack((byte) (r_PC >> 8));
        pushStack((byte) (r_PC));

        int flags = (f_N ? 1 : 0) << 7 | (f_V ? 1 : 0) << 6 | 1 << 5 | ((type == InterruptType.BRK_) ? 1 : 0) << 4 | (f_D ? 1 : 0) << 3 | (f_I ? 1 : 0) << 2 | (f_Z ? 1 : 0) << 1 | (f_C ? 1 : 0);
        pushStack((byte) flags);

        f_I = true;

        switch (type) {
            case IRQ:
            case BRK_:
                r_PC = readAddress(IRQVector);
                break;
            case NMI:
                r_PC = readAddress(NMIVector);
                break;
        }
        m_skipCycles += 7;
    }

    private void pushStack(byte value) {
        m_bus.write(0x100 | r_SP, value);
        r_SP = (r_SP - 1) & 0xFF; // Wrap SP
    }

    private byte pullStack() {
        r_SP = (r_SP + 1) & 0xFF; // Wrap SP
        return m_bus.read(0x100 | r_SP);
    }

    private void setZN(int value) {
        value &= 0xFF; // Ensure we are checking a byte
        f_Z = (value == 0);
        f_N = (value & 0x80) != 0;
    }

    private void skipPageCrossCycle(int a, int b) {
        if ((a & 0xff00) != (b & 0xff00)) m_skipCycles += 1;
    }

    private int readAddress(int addr) {
        // Read low byte, Read high byte, combine
        int low = m_bus.read(addr) & 0xFF;
        int high = m_bus.read(addr + 1) & 0xFF;
        return low | (high << 8);
    }

    // =============================================================
    // EXECUTION UNITS (Ported Logic)
    // =============================================================

    private boolean executeImplied(int opcode) {
        OperationImplied op = OperationImplied.fromOpcode(opcode);
        if (op == null) return false;

        switch (op) {
            case NOP:
                break;
            case BRK:
                interruptSequence(InterruptType.BRK_);
                break;
            case JSR:
                pushStack((byte) ((r_PC + 1) >> 8));
                pushStack((byte) (r_PC + 1));
                r_PC = readAddress(r_PC);
                break;
            case RTS:
                byte b6 = pullStack();
                r_PC = b6 & 0xFF;
                byte b5 = pullStack();
                r_PC |= (b5 & 0xFF) << 8;
                r_PC++;
                break;
            case RTI:
                byte b4 = pullStack();
                int flags = b4 & 0xFF;
                f_N = (flags & 0x80) != 0;
                f_V = (flags & 0x40) != 0;
                f_D = (flags & 0x8) != 0;
                f_I = (flags & 0x4) != 0;
                f_Z = (flags & 0x2) != 0;
                f_C = (flags & 0x1) != 0;
                byte b3 = pullStack();
                r_PC = b3 & 0xFF;
                byte b2 = pullStack();
                r_PC |= (b2 & 0xFF) << 8;
                break;
            case JMP:
                r_PC = readAddress(r_PC);
                break;
            case JMPI: // Indirect JMP with page boundary bug
                int location = readAddress(r_PC);
                int page = location & 0xff00;
                int low = m_bus.read(location) & 0xFF;
                int high = m_bus.read(page | ((location + 1) & 0xff)) & 0xFF;
                r_PC = low | (high << 8);
                break;
            case PHP:
                int pFlags = (f_N ? 1 : 0) << 7 | (f_V ? 1 : 0) << 6 | 1 << 5 | 1 << 4 | (f_D ? 1 : 0) << 3 | (f_I ? 1 : 0) << 2 | (f_Z ? 1 : 0) << 1 | (f_C ? 1 : 0);
                pushStack((byte) pFlags);
                break;
            case PLP:
                byte b1 = pullStack();
                flags = b1 & 0xFF;
                f_N = (flags & 0x80) != 0;
                f_V = (flags & 0x40) != 0;
                f_D = (flags & 0x8) != 0;
                f_I = (flags & 0x4) != 0;
                f_Z = (flags & 0x2) != 0;
                f_C = (flags & 0x1) != 0;
                break;
            case PHA:
                pushStack((byte) r_A);
                break;
            case PLA:
                byte b = pullStack();
                r_A = b & 0xFF;
                setZN(r_A);
                break;
            case DEY:
                r_Y = (r_Y - 1) & 0xFF;
                setZN(r_Y);
                break;
            case DEX:
                r_X = (r_X - 1) & 0xFF;
                setZN(r_X);
                break;
            case TAY:
                r_Y = r_A;
                setZN(r_Y);
                break;
            case INY:
                r_Y = (r_Y + 1) & 0xFF;
                setZN(r_Y);
                break;
            case INX:
                r_X = (r_X + 1) & 0xFF;
                setZN(r_X);
                break;
            case CLC:
                f_C = false;
                break;
            case SEC:
                f_C = true;
                break;
            case CLI:
                f_I = false;
                break;
            case SEI:
                f_I = true;
                break;
            case CLD:
                f_D = false;
                break;
            case SED:
                f_D = true;
                break;
            case TYA:
                r_A = r_Y;
                setZN(r_A);
                break;
            case CLV:
                f_V = false;
                break;
            case TXA:
                r_A = r_X;
                setZN(r_A);
                break;
            case TXS:
                r_SP = r_X;
                break;
            case TAX:
                r_X = r_A;
                setZN(r_X);
                break;
            case TSX:
                r_X = r_SP;
                setZN(r_X);
                break;
            default:
                return false;
        }
        return true;
    }

    private boolean executeBranch(int opcode) {
        if ((opcode & BranchInstructionMask) == BranchInstructionMaskResult) {
            boolean branch = (opcode & BranchConditionMask) != 0;
            int flagCheck = (opcode >> BranchOnFlagShift) & 0x3; // Mask to safe range

            switch (flagCheck) {
                case 0: /* Negative */
                    branch = branch == f_N;
                    break;
                case 1: /* Overflow */
                    branch = branch == f_V;
                    break;
                case 2: /* Carry    */
                    branch = branch == f_C;
                    break;
                case 3: /* Zero     */
                    branch = branch == f_Z;
                    break;
                default:
                    return false;
            }

            if (branch) {
                byte offset = m_bus.read(r_PC++); // Signed offset!
                m_skipCycles++;
                int newPC = (r_PC + offset) & 0xFFFF;
                skipPageCrossCycle(r_PC, newPC);
                r_PC = newPC;
            } else {
                r_PC++;
            }
            return true;
        }
        return false;
    }

    private boolean executeType1(int opcode) {
        if ((opcode & InstructionModeMask) == 0x1) {
            int location = 0;
            Operation1 op = Operation1.fromInt((opcode & OperationMask) >> OperationShift);
            AddrMode1 addrMode = AddrMode1.fromInt((opcode & AddrModeMask) >> AddrModeShift);

            switch (addrMode) {
                case IndexedIndirectX -> {
                    int zero_addr = (r_X + m_bus.read(r_PC++)) & 0xFF;
                    int low = m_bus.read(zero_addr) & 0xFF;
                    int high = m_bus.read((zero_addr + 1) & 0xFF) & 0xFF;
                    location = low | (high << 8);
                }
                case ZeroPage -> location = m_bus.read(r_PC++) & 0xFF;
                case Immediate -> location = r_PC++;
                case Absolute -> {
                    location = readAddress(r_PC);
                    r_PC += 2;
                }
                case IndirectY -> {
                    int zAddr = m_bus.read(r_PC++) & 0xFF;
                    int l = m_bus.read(zAddr) & 0xFF;
                    int h = m_bus.read((zAddr + 1) & 0xFF) & 0xFF;
                    location = l | (h << 8);
                    if (op != Operation1.STA) skipPageCrossCycle(location, location + r_Y);
                    location = (location + r_Y) & 0xFFFF;
                }
                case IndexedX -> location = (m_bus.read(r_PC++) + r_X) & 0xFF; // Zero page wrap
                case AbsoluteY -> {
                    location = readAddress(r_PC);
                    r_PC += 2;
                    if (op != Operation1.STA) skipPageCrossCycle(location, location + r_Y);
                    location = (location + r_Y) & 0xFFFF;
                }
                case AbsoluteX -> {
                    location = readAddress(r_PC);
                    r_PC += 2;
                    if (op != Operation1.STA) skipPageCrossCycle(location, location + r_X);
                    location = (location + r_X) & 0xFFFF;
                }
                default -> {
                    return false;
                }
            }

            switch (op) {
                case ORA -> {
                    r_A |= (m_bus.read(location) & 0xFF);
                    setZN(r_A);
                }
                case AND -> {
                    r_A &= (m_bus.read(location) & 0xFF);
                    setZN(r_A);
                }
                case EOR -> {
                    r_A ^= (m_bus.read(location) & 0xFF);
                    setZN(r_A);
                }
                case ADC -> {
                    int operand = m_bus.read(location) & 0xFF;
                    int sum = r_A + operand + (f_C ? 1 : 0);
                    f_C = (sum & 0x100) != 0;
                    f_V = ((r_A ^ sum) & (operand ^ sum) & 0x80) != 0;
                    r_A = sum & 0xFF;
                    setZN(r_A);
                }
                case STA -> m_bus.write(location, (byte) r_A);
                case LDA -> {
                    r_A = m_bus.read(location) & 0xFF;
                    setZN(r_A);
                }
                case SBC -> {
                    int subtrahend = m_bus.read(location) & 0xFF;
                    int diff = r_A - subtrahend - (f_C ? 0 : 1); // Logical NOT C
                    f_C = (diff & 0x100) == 0; // Clear carry if borrow
                    f_V = ((r_A ^ diff) & ((~subtrahend) ^ diff) & 0x80) != 0;
                    r_A = diff & 0xFF;
                    setZN(r_A);
                }
                case CMP -> {
                    int val = m_bus.read(location) & 0xFF;
                    int res = r_A - val;
                    f_C = res >= 0;
                    setZN(res);
                }
                default -> {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean executeType2(int opcode) {
        if ((opcode & InstructionModeMask) == 2) {
            int location = 0;
            Operation2 op = Operation2.fromInt((opcode & OperationMask) >> OperationShift);
            AddrMode2 addrMode = AddrMode2.fromInt((opcode & AddrModeMask) >> AddrModeShift);

            switch (addrMode) {
                case Immediate_:
                    location = r_PC++;
                    break;
                case ZeroPage_:
                    location = m_bus.read(r_PC++) & 0xFF;
                    break;
                case Accumulator:
                    break;
                case Absolute_:
                    location = readAddress(r_PC);
                    r_PC += 2;
                    break;
                case Indexed:
                    location = m_bus.read(r_PC++) & 0xFF;
                    int index = (op == Operation2.LDX || op == Operation2.STX) ? r_Y : r_X;
                    location = (location + index) & 0xFF;
                    break;
                case AbsoluteIndexed:
                    location = readAddress(r_PC);
                    r_PC += 2;
                    index = (op == Operation2.LDX || op == Operation2.STX) ? r_Y : r_X;
                    skipPageCrossCycle(location, location + index);
                    location = (location + index) & 0xFFFF;
                    break;
                default:
                    return false;
            }

            int operand = 0;
            boolean isAcc = (addrMode == AddrMode2.Accumulator);

            switch (op) {
                case ASL:
                case ROL:
                    int val = isAcc ? r_A : (m_bus.read(location) & 0xFF);
                    boolean oldC = f_C;
                    f_C = (val & 0x80) != 0;
                    val = (val << 1) & 0xFF;
                    if (op == Operation2.ROL && oldC) val |= 1;
                    setZN(val);
                    if (isAcc) r_A = val;
                    else m_bus.write(location, (byte) val);
                    break;
                case LSR:
                case ROR:
                    val = isAcc ? r_A : (m_bus.read(location) & 0xFF);
                    oldC = f_C;
                    f_C = (val & 1) != 0;
                    val = (val >> 1) & 0xFF;
                    if (op == Operation2.ROR && oldC) val |= 0x80;
                    setZN(val);
                    if (isAcc) r_A = val;
                    else m_bus.write(location, (byte) val);
                    break;
                case STX:
                    m_bus.write(location, (byte) r_X);
                    break;
                case LDX:
                    r_X = m_bus.read(location) & 0xFF;
                    setZN(r_X);
                    break;
                case DEC:
                    int tmp = (m_bus.read(location) - 1) & 0xFF;
                    setZN(tmp);
                    m_bus.write(location, (byte) tmp);
                    break;
                case INC:
                    tmp = (m_bus.read(location) + 1) & 0xFF;
                    setZN(tmp);
                    m_bus.write(location, (byte) tmp);
                    break;
                default:
                    return false;
            }
            return true;
        }
        return false;
    }

    private boolean executeType0(int opcode) {
        if ((opcode & InstructionModeMask) == 0x0) {
            int location = 0;
            AddrMode2 addrMode = AddrMode2.fromInt((opcode & AddrModeMask) >> AddrModeShift);

            // Logic identical to Type2 AddrMode except for some mappings
            // Simplified for space:
            switch (addrMode) {
                case Immediate_ -> location = r_PC++;
                case ZeroPage_ -> location = m_bus.read(r_PC++) & 0xFF;
                case Absolute_ -> {
                    location = readAddress(r_PC);
                    r_PC += 2;
                }
                case Indexed -> location = (m_bus.read(r_PC++) + r_X) & 0xFF;
                case AbsoluteIndexed -> {
                    location = readAddress(r_PC);
                    r_PC += 2;
                    skipPageCrossCycle(location, location + r_X);
                    location += r_X;
                }
                default -> {
                    return false;
                }
            }

            Operation0 op = Operation0.fromInt((opcode & OperationMask) >> OperationShift);
            switch (op) {
                case BIT:
                    int operand = m_bus.read(location) & 0xFF;
                    f_Z = (r_A & operand) == 0;
                    f_V = (operand & 0x40) != 0;
                    f_N = (operand & 0x80) != 0;
                    break;
                case STY:
                    m_bus.write(location, (byte) r_Y);
                    break;
                case LDY:
                    r_Y = m_bus.read(location) & 0xFF;
                    setZN(r_Y);
                    break;
                case CPY:
                    int diff = r_Y - (m_bus.read(location) & 0xFF);
                    f_C = diff >= 0;
                    setZN(diff);
                    break;
                case CPX:
                    diff = r_X - (m_bus.read(location) & 0xFF);
                    f_C = diff >= 0;
                    setZN(diff);
                    break;
                default:
                    return false;
            }
            return true;
        }
        return false;
    }
}
