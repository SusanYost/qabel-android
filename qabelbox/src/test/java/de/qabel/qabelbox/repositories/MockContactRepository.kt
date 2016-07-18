package de.qabel.qabelbox.repositories

import de.qabel.core.config.Contact
import de.qabel.core.config.Contacts
import de.qabel.core.config.Identity
import de.qabel.desktop.repository.ContactRepository
import de.qabel.desktop.repository.exception.EntityNotFoundException
import de.qabel.desktop.util.DefaultHashMap
import de.qabel.qabelbox.contacts.dto.ContactDto
import java.util.*

class MockContactRepository(val contacts: MutableMap<String, Contact> = mutableMapOf(),
                            val identityMapping: DefaultHashMap<Identity, MutableSet<String>> = DefaultHashMap({ key -> HashSet() })) : ContactRepository {

    constructor(contact: Contact, identity: Identity) : this() {
        save(contact, identity)
    }

    override fun find(identity: Identity): Contacts {
        val identityContacts = identityMapping.getOrDefault(identity);
        val resultContacts = Contacts(identity);
        for (contactKey in identityContacts) {
            resultContacts.put(this.contacts[contactKey])
        }
        return resultContacts;
    }

    override fun save(contact: Contact, identity: Identity) {
        contacts.put(contact.keyIdentifier, contact)
        identityMapping.getOrDefault(identity).add(contact.keyIdentifier);
    }

    override fun delete(contact: Contact, identity: Identity) {
        contacts.remove(contact.keyIdentifier)
        identityMapping.getOrDefault(identity).remove(contact.keyIdentifier);
    }

    override fun findByKeyId(identity: Identity, keyId: String): Contact {
        if (identityMapping.getOrDefault(identity).contains(keyId)) {
            return findByKeyId(keyId)
        } else throw EntityNotFoundException("Contact not found for Identity!");
    }

    override fun findByKeyId(keyId: String): Contact {
        if (contacts.contains(keyId)) {
            return contacts[keyId]!!
        } else throw EntityNotFoundException("Contact not found!");
    }

    override fun exists(contact: Contact): Boolean {
        return contacts.contains(contact.keyIdentifier)
    }

    override fun findContactWithIdentities(key: String): Pair<Contact, List<Identity>> {
        if (contacts.contains(key)) {
            return contacts[key].let { contact -> Pair(contact!!, findContactIdentities(contact.keyIdentifier)) }
        } else throw EntityNotFoundException("Contact is not one of the injected")
    }

    override fun findWithIdentities(searchString: String): Collection<Pair<Contact, List<Identity>>> {
        return contacts.values
                .filter { contact -> contact.alias.toLowerCase().startsWith(searchString.toLowerCase()) }
                .map { contact -> Pair(contact, findContactIdentities(contact.keyIdentifier)) }
    }

    private fun findContactIdentities(key: String): List<Identity> {
        val identities = mutableListOf<Identity>();
        for ((identity, contactKeys) in identityMapping) {
            if (contactKeys.contains(key)) {
                identities.add(identity);
            }
        }
        return identities;
    }

}