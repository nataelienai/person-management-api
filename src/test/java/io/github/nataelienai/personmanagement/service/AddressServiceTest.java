package io.github.nataelienai.personmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.nataelienai.personmanagement.dto.AddressRequest;
import io.github.nataelienai.personmanagement.dto.AddressResponse;
import io.github.nataelienai.personmanagement.entity.Address;
import io.github.nataelienai.personmanagement.entity.Person;
import io.github.nataelienai.personmanagement.exception.AddressNotFoundException;
import io.github.nataelienai.personmanagement.exception.PersonNotFoundException;
import io.github.nataelienai.personmanagement.repository.AddressRepository;
import io.github.nataelienai.personmanagement.repository.PersonRepository;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {
  final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  AddressService addressService;

  @Mock
  AddressRepository addressRepository;

  @Mock
  PersonRepository personRepository;

  @Captor
  ArgumentCaptor<Set<Address>> addressesCaptor;

  @BeforeEach
  void setUp() {
    addressService = new AddressService(addressRepository, personRepository);
  }

  @Test
  @DisplayName("create() should throw when person id does not exist")
  void create_shouldThrow_whenPersonIdDoesNotExist() {
    // given
    Long personId = 1L;
    AddressRequest addressRequest = new AddressRequest("12345-123", "City", "Street", 10);

    given(personRepository.findById(personId)).willReturn(Optional.empty());

    // when
    // then
    assertThatThrownBy(() -> addressService.create(personId, addressRequest))
        .isInstanceOf(PersonNotFoundException.class);
  }

  @Test
  @DisplayName("create() should save address when given a valid address request")
  void create_shouldSaveAddress_whenGivenValidAddressRequest() {
    // given
    Person person = createPerson();
    Long personId = person.getId();
    AddressRequest addressRequest = new AddressRequest("12345-123", "City", "Street", 10);

    given(personRepository.findById(personId)).willReturn(Optional.of(person));
    given(addressRepository.save(any(Address.class))).will(returnsFirstArg());

    // when
    AddressResponse addressResponse = addressService.create(personId, addressRequest);

    // then
    ArgumentCaptor<Address> addressArgumentCaptor = ArgumentCaptor.forClass(Address.class);
    then(addressRepository).should().save(addressArgumentCaptor.capture());
    Address capturedAddress = addressArgumentCaptor.getValue();

    assertThat(capturedAddress.getPerson()).isEqualTo(person);

    assertThat(addressResponse)
        .usingRecursiveComparison()
        .ignoringFields("id", "main")
        .isEqualTo(addressRequest);
    assertThat(addressResponse.getMain()).isFalse();
  }

  @Test
  @DisplayName("findAllByPersonId() should throw when person id does not exist")
  void findAllByPersonId_shouldThrow_whenPersonIdDoesNotExist() {
    // given
    Long personId = 1L;
    given(personRepository.findById(personId)).willReturn(Optional.empty());

    // when
    // then
    assertThatThrownBy(() -> addressService.findAllByPersonId(personId))
        .isInstanceOf(PersonNotFoundException.class);
  }

  @Test
  @DisplayName("findAllByPersonId() should retrieve a person's addresses when their id exists")
  void findAllByPersonId_shouldRetrievePersonAddresses_whenTheirIdExists() {
    // given
    Person person = createPerson();
    Long personId = person.getId();
    Address address = createAddress(1L, person);

    given(personRepository.findById(personId)).willReturn(Optional.of(person));

    // when
    List<AddressResponse> addressResponses = addressService.findAllByPersonId(personId);

    // then
    assertThat(addressResponses).isNotEmpty();
    assertThat(addressResponses.get(0))
        .usingRecursiveComparison()
        .ignoringFields("person")
        .isEqualTo(address);
  }

  @Test
  @DisplayName("setPersonAddressAsMain() should throw when person id does not exist")
  void setPersonAddressAsMain_shouldThrow_whenPersonIdDoesNotExist() {
    // given
    Long personId = 1L;
    given(personRepository.findById(personId)).willReturn(Optional.empty());

    // when
    // then
    assertThatThrownBy(() -> addressService.setPersonAddressAsMain(personId, 1L))
        .isInstanceOf(PersonNotFoundException.class);
  }

  @Test
  @DisplayName("setPersonAddressAsMain() should throw when person does not have the address")
  void setPersonAddressAsMain_shouldThrow_whenPersonDoesNotHaveAddress() {
    // given
    Person person = createPerson();
    Long personId = person.getId();
    given(personRepository.findById(personId)).willReturn(Optional.of(person));

    // when
    // then
    assertThatThrownBy(() -> addressService.setPersonAddressAsMain(personId, 1L))
        .isInstanceOf(AddressNotFoundException.class);
  }

  @Test
  @DisplayName("setPersonAddressAsMain() should set address as main when person and address ids exist")
  void setPersonAddressAsMain_shouldSetAddressAsMain_whenPersonAndAddressIdsExist() {
    // given
    Long addressId = 1L;
    Person person = createPerson();
    createAddress(addressId, person);
    createAddress(addressId + 1, person);
    Long personId = person.getId();

    given(personRepository.findById(personId)).willReturn(Optional.of(person));
    given(addressRepository.saveAll(anyIterable())).willReturn(List.of());

    // when
    addressService.setPersonAddressAsMain(personId, addressId);

    // then
    then(addressRepository).should().saveAll(addressesCaptor.capture());
    Set<Address> capturedAddresses = addressesCaptor.getValue();

    assertThat(capturedAddresses).hasSize(2);
    assertThat(capturedAddresses)
        .usingRecursiveComparison()
        .ignoringCollectionOrder()
        .ignoringFields("main")
        .isEqualTo(person.getAddresses());

    for (Address address : capturedAddresses) {
      if (addressId == address.getId()) {
        assertThat(address.getMain()).isTrue();
      } else {
        assertThat(address.getMain()).isFalse();
      }
    }
  }

  Address createAddress(Long id, Person person) {
    Address address = new Address(id, "12345-123", "City", "Street", 10, false, person);
    person.getAddresses().add(address);

    return address;
  }

  Person createPerson() {
    Person person = new Person();
    person.setId(1L);
    person.setName("Kendrick Lamar");
    person.setDateOfBirth(LocalDate.parse("2000-01-01", DATE_FORMATTER));

    return person;
  }
}
