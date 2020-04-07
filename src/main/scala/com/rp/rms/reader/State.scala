package com.rp.rms.reader

import com.rp.rms.commons
import com.rp.rms.writer.{Business, Connection}

import scala.collection.immutable.MultiDict


object State {
  def empty(): State = new State(MultiDict.empty, MultiDict.empty, MultiDict.empty)
}

case class State(friends: MultiDict[String, String], relatives: MultiDict[String, String], businesses: MultiDict[String, String]) {
  def updated(event: commons.Event): State = {
    event match {
      case Connection.BecameFriends(one, another) => {
        copy(friends = this.friends
          .add(one, another)
          .add(another, one)
        )
      }
      case Connection.BecameRelatives(one, another) => {
        copy(relatives = this.relatives
          .add(one, another)
          .add(another, one)
        )
      }
      case Business.Joined(business, employee) => {
        copy(businesses = this.businesses
          .add(business, employee)
        )
      }
    }
  }

  def emplyedRelativeFriends(): Set[String] = {
    businesses.values
              .flatMap(e => relatives.get(e))
              .flatMap(e => friends.get(e))
              .toSet
  }

  def businessEmployeeRelatives(business:String): Set[String] = {
    businesses.get(business)
      .flatMap(e => relatives.get(e))
      .toSet
  }

  def businessWithStrengthGreaterThan(strength:Int): Set[String] = {
    businesses.keySet
      .filter(business => businesses.get(business).size > strength)
      .toSet
  }
}
