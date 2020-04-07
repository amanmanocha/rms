package com.rp.rms

object commons {

  trait Event extends CborSerializable

  trait Command extends CborSerializable
  trait Confirmation extends CborSerializable
  trait Summary
  final case class Accepted(summary: Summary) extends Confirmation
  final case class Rejected(reason: String) extends Confirmation
}
