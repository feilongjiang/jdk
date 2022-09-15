/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Huawei Technologies Co., Ltd. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "classfile/javaClasses.hpp"
#include "code/vmreg.hpp"
#include "precompiled.hpp"
#include "prims/foreignGlobals.hpp"
#include "prims/foreignGlobals.inline.hpp"
#include "runtime/jniHandles.inline.hpp"
#include "utilities/debug.hpp"

class MacroAssembler;

static constexpr int INTEGER_TYPE = 0;
static constexpr int FLOAT_TYPE = 1;

const ABIDescriptor ForeignGlobals::parse_abi_descriptor(jobject jabi) {
  oop abi_oop = JNIHandles::resolve_non_null(jabi);
  ABIDescriptor abi;

  objArrayOop inputStorage = jdk_internal_foreign_abi_ABIDescriptor::inputStorage(abi_oop);
  parse_register_array(inputStorage, INTEGER_TYPE, abi._integer_argument_registers, as_Register);
  parse_register_array(inputStorage, FLOAT_TYPE, abi._float_argument_registers, as_FloatRegister);

  objArrayOop outputStorage = jdk_internal_foreign_abi_ABIDescriptor::outputStorage(abi_oop);
  parse_register_array(outputStorage, INTEGER_TYPE, abi._integer_return_registers, as_Register);
  parse_register_array(outputStorage, FLOAT_TYPE, abi._float_return_registers, as_FloatRegister);

  objArrayOop volatileStorage = jdk_internal_foreign_abi_ABIDescriptor::volatileStorage(abi_oop);
  parse_register_array(volatileStorage, INTEGER_TYPE, abi._integer_additional_volatile_registers, as_Register);
  parse_register_array(volatileStorage, FLOAT_TYPE, abi._float_additional_volatile_registers, as_FloatRegister);

  abi._stack_alignment_bytes = jdk_internal_foreign_abi_ABIDescriptor::stackAlignment(abi_oop);
  abi._shadow_space_bytes = jdk_internal_foreign_abi_ABIDescriptor::shadowSpace(abi_oop);

  abi._target_addr_reg = parse_vmstorage(
          jdk_internal_foreign_abi_ABIDescriptor::targetAddrStorage(abi_oop))->as_Register();
  abi._ret_buf_addr_reg = parse_vmstorage(
          jdk_internal_foreign_abi_ABIDescriptor::retBufAddrStorage(abi_oop))->as_Register();
  return abi;
}

static RegType get_regtype(int regtype_or_storageclass) {
  if (regtype_or_storageclass <= static_cast<int>(RegType::STACK)) {
    return static_cast<RegType>(regtype_or_storageclass);
  }

  switch (static_cast<StorageClass>(regtype_or_storageclass)) {
    case StorageClass::INTEGER_8:
    case StorageClass::INTEGER_16:
    case StorageClass::INTEGER_32:
    case StorageClass::INTEGER_64:
      return RegType::INTEGER;
    case StorageClass::FLOAT_32:
    case StorageClass::FLOAT_64:
      return RegType::FLOAT;
    default:
      ShouldNotReachHere();
      return static_cast<RegType>(-1);
  }
}

VMReg ForeignGlobals::vmstorage_to_vmreg(int type, int index) {
  switch (get_regtype(type)) {
    case RegType::INTEGER:
      return ::as_Register(index)->as_VMReg();
    case RegType::FLOAT:
      return ::as_FloatRegister(index)->as_VMReg();
    case RegType::STACK:
      return VMRegImpl::stack2reg(index LP64_ONLY(*2));
    default:
      return VMRegImpl::Bad();
  }
}

int RegSpiller::pd_reg_size(VMReg reg) {
  if (reg->is_Register()) {
    return 8;
  } else if (reg->is_FloatRegister()) {
    return 8;
  }
  return 0; // stack and BAD
}

// pd_* are used to perfrom upcall, do not impelment them now.
void RegSpiller::pd_store_reg(MacroAssembler* masm, int offset, VMReg reg) {
  if (reg->is_Register()) {
    masm->sd(reg->as_Register(), Address(sp, offset));
  } else if (reg->is_FloatRegister()) {
    masm->fsd(reg->as_FloatRegister(), Address(sp, offset));
  } else {
    // stack and BAD
  }
}

void RegSpiller::pd_load_reg(MacroAssembler* masm, int offset, VMReg reg) {
  if (reg->is_Register()) {
    masm->ld(reg->as_Register(), Address(sp, offset));
  } else if (reg->is_FloatRegister()) {
    masm->fld(reg->as_FloatRegister(), Address(sp, offset));
  } else {
    // stack and BAD
  }
}

// The java_calling_convention describes stack locations as ideal slots on
// a frame with no abi restrictions. Since we must observe abi restrictions
// (like the placement of the register window) the slots must be biased by
// the following value.
static int reg2offset_in(VMReg r) {
  // Account for saved fp and ra
  // This should really be in_preserve_stack_slots
  return r->reg2stack() * VMRegImpl::stack_slot_size;
}

static int reg2offset_out(VMReg r) {
  return (r->reg2stack() + SharedRuntime::out_preserve_stack_slots()) * VMRegImpl::stack_slot_size;
}

#define __ _masm->

// A long move
static void long_move(MacroAssembler* _masm, VMRegPair src, VMRegPair dst) {
  assert_cond(_masm != NULL);
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      __ ld(t0, Address(fp, reg2offset_in(src.first())));
      __ sd(t0, Address(sp, reg2offset_out(dst.first())));
    } else {
      // stack to reg
      __ ld(dst.first()->as_Register(), Address(fp, reg2offset_in(src.first())));
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    __ sd(src.first()->as_Register(), Address(sp, reg2offset_out(dst.first())));
  } else {
    if (dst.first() != src.first()) {
      __ mv(dst.first()->as_Register(), src.first()->as_Register());
    }
  }
}

// On 64 bit we will store integer like items to the stack as
// 64 bits items (riscv64 abi) even though java would only store
// 32bits for a parameter. On 32bit it will simply be 32 bits
// So this routine will do 32->32 on 32bit and 32->64 on 64bit
static void move32_64(MacroAssembler* _masm, VMRegPair src, VMRegPair dst) {
  assert_cond(_masm != NULL);
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      // stack to stack
      __ ld(t0, Address(fp, reg2offset_in(src.first())));
      __ sd(t0, Address(sp, reg2offset_out(dst.first())));
    } else {
      // stack to reg
      __ lw(dst.first()->as_Register(), Address(fp, reg2offset_in(src.first())));
    }
  } else if (dst.first()->is_stack()) {
    // reg to stack
    __ sd(src.first()->as_Register(), Address(sp, reg2offset_out(dst.first())));
  } else {
    if (dst.first() != src.first()) {
      // 32bits extend sign
      __ addw(dst.first()->as_Register(), src.first()->as_Register(), zr);
    }
  }
}

// A double move
static void double_move(MacroAssembler* _masm, VMRegPair src, VMRegPair dst) {
  assert(src.first()->is_stack() && dst.first()->is_stack() ||
         src.first()->is_reg() && dst.first()->is_reg() || src.first()->is_stack() && dst.first()->is_reg(),
         "Unexpected error");
  assert_cond(_masm != NULL);
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      __ ld(t0, Address(fp, reg2offset_in(src.first())));
      __ sd(t0, Address(sp, reg2offset_out(dst.first())));
    } else if (dst.first()->is_Register()) {
      __ ld(dst.first()->as_Register(), Address(fp, reg2offset_in(src.first())));
    } else {
      ShouldNotReachHere();
    }
  } else if (src.first() != dst.first()) {
    if (src.is_single_phys_reg() && dst.is_single_phys_reg()) {
      __ fmv_d(dst.first()->as_FloatRegister(), src.first()->as_FloatRegister());
    } else {
      ShouldNotReachHere();
    }
  }
}

// A float arg may have to do float reg int reg conversion
static void float_move(MacroAssembler* _masm, VMRegPair src, VMRegPair dst) {
  assert(src.first()->is_stack() && dst.first()->is_stack() ||
         src.first()->is_reg() && dst.first()->is_reg() || src.first()->is_stack() && dst.first()->is_reg(),
         "Unexpected error");
  assert_cond(_masm != NULL);
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      __ lwu(t0, Address(fp, reg2offset_in(src.first())));
      __ sw(t0, Address(sp, reg2offset_out(dst.first())));
    } else if (dst.first()->is_Register()) {
      __ lwu(dst.first()->as_Register(), Address(fp, reg2offset_in(src.first())));
    } else {
      ShouldNotReachHere();
    }
  } else if (src.first() != dst.first()) {
    if (src.is_single_phys_reg() && dst.is_single_phys_reg()) {
      __ fmv_s(dst.first()->as_FloatRegister(), src.first()->as_FloatRegister());
    } else {
      ShouldNotReachHere();
    }
  }
}

static void move_float_to_integer_or_stack(MacroAssembler* _masm, VMRegPair src, VMRegPair dst) {
  assert_cond(_masm != NULL);

  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      __ lwu(t0, Address(fp, reg2offset_in(src.first())));
      __ sw(t0, Address(sp, reg2offset_out(dst.first())));
    } else if (dst.first()->is_Register()) {
      __ lwu(dst.first()->as_Register(), Address(fp, reg2offset_in(src.first())));
    } else {
      ShouldNotReachHere();
    }
  } else if (src.first() != dst.first()) {
    // java abi will not use integer reg to pass a float.
    if (src.first()->is_FloatRegister()) {
      if (dst.first()->is_Register())
        __ fmv_x_w(dst.first()->as_Register(), src.first()->as_FloatRegister());
      else
        __ fsw(src.first()->as_FloatRegister(), Address(sp, reg2offset_out(dst.first())));
    } else
      ShouldNotReachHere();
  }
}

static void move_double_to_integer_or_stack(MacroAssembler* _masm, VMRegPair src, VMRegPair dst) {
  assert_cond(_masm != NULL);
  if (src.first()->is_stack()) {
    if (dst.first()->is_stack()) {
      __ ld(t0, Address(fp, reg2offset_in(src.first())));
      __ sd(t0, Address(sp, reg2offset_out(dst.first())));
    } else if (dst.first()->is_Register()) {
      __ ld(dst.first()->as_Register(), Address(fp, reg2offset_in(src.first())));
    } else {
      ShouldNotReachHere();
    }
  } else if (src.first() != dst.first()) {
    if (src.is_single_phys_reg() && dst.is_single_phys_reg()) {
      __ fmv_x_d(dst.first()->as_Register(), src.first()->as_FloatRegister());
    } else {
      ShouldNotReachHere();
    }
  }
}

#undef __

void ArgumentShuffle::pd_generate(MacroAssembler* masm, VMReg tmp, int in_stk_bias, int out_stk_bias) const {
  Register tmp_reg = tmp->as_Register();
  for (int i = 0; i < _moves.length(); i++) {
    Move move = _moves.at(i);
    BasicType arg_bt = move.bt;
    VMRegPair from_vmreg = move.from;
    VMRegPair to_vmreg = move.to;

    masm->block_comment(err_msg("bt=%s", null_safe_string(type2name(arg_bt))));
    switch (arg_bt) {
      case T_BOOLEAN:
      case T_BYTE:
      case T_SHORT:
      case T_CHAR:
      case T_INT:
        move32_64(masm, from_vmreg, to_vmreg);
        break;
      case T_FLOAT: {
        if (!to_vmreg.first()->is_FloatRegister()) {
          move_float_to_integer_or_stack(masm, from_vmreg, to_vmreg);
        } else {
          float_move(masm, from_vmreg, to_vmreg);
        }
        break;
      }
      case T_DOUBLE: {
        if (!to_vmreg.first()->is_FloatRegister()) {
          move_double_to_integer_or_stack(masm, from_vmreg, to_vmreg);
        } else {
          double_move(masm, from_vmreg, to_vmreg);
        }
        break;
      }
      case T_LONG:
        long_move(masm, from_vmreg, to_vmreg);
        break;
      default:
        fatal("found in upcall args: %s", type2name(arg_bt));
    }
  }
}

bool ABIDescriptor::is_volatile_reg(Register reg) const {
  return _integer_argument_registers.contains(reg)
         || _integer_additional_volatile_registers.contains(reg);
}

bool ABIDescriptor::is_volatile_reg(FloatRegister reg) const {
  return _float_argument_registers.contains(reg)
         || _float_additional_volatile_registers.contains(reg);
}
