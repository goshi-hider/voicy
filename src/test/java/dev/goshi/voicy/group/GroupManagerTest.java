package dev.goshi.voicy.group;

import dev.goshi.voicy.group.GroupManager.Result;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class GroupManagerTest {

	private final AtomicLong now = new AtomicLong(1_000L);
	private final GroupManager groups = new GroupManager(now::get);
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();
	private final UUID carol = UUID.randomUUID();

	private void aliceCreates(boolean isPublic, int max) {
		assertEquals(Result.OK, groups.create(alice, "Alice", "Team", isPublic, max));
	}

	@Test
	void createValidatesNameAndSize() {
		assertEquals(Result.INVALID_NAME, groups.create(alice, "Alice", "a", true, 8));
		assertEquals(Result.INVALID_NAME, groups.create(alice, "Alice", "has space", true, 8));
		assertEquals(Result.INVALID_SIZE, groups.create(alice, "Alice", "Team", true, 1));
		assertEquals(Result.INVALID_SIZE, groups.create(alice, "Alice", "Team", true, 33));
		aliceCreates(true, 8);
		assertEquals(Result.ALREADY_IN_GROUP, groups.create(alice, "Alice", "Other", true, 8));
		assertEquals(Result.NAME_TAKEN, groups.create(bob, "Bob", "team", true, 8));
	}

	@Test
	void creatorOwnsGroupAndTalksToItByDefault() {
		aliceCreates(false, 4);
		assertEquals(alice, groups.groupOf(alice).owner());
		assertNotNull(groups.talkGroup(alice));
		assertEquals(Result.OK, groups.setTalkToGroupOnly(alice, false));
		assertNull(groups.talkGroup(alice));
	}

	@Test
	void inviteFlowNeedsOwnerAndAcceptance() {
		aliceCreates(false, 4);
		assertEquals(Result.NOT_IN_GROUP, groups.invite(bob, "Bob", carol));
		assertEquals(Result.OK, groups.invite(alice, "Alice", bob));
		assertEquals(Result.ALREADY_INVITED, groups.invite(alice, "Alice", bob));
		assertEquals(1, groups.invitesFor(bob).size());
		UUID groupId = groups.groupOf(alice).id();
		assertEquals(Result.JOINED, groups.acceptInvite(bob, "Bob", groupId));
		assertTrue(groups.groupOf(alice).isMember(bob));
		assertTrue(groups.invitesFor(bob).isEmpty());
		assertEquals(Result.NOT_OWNER, groups.invite(bob, "Bob", carol));
		assertEquals(Result.TARGET_IN_GROUP, groups.invite(alice, "Alice", bob));
	}

	@Test
	void declinedInviteIsGone() {
		aliceCreates(false, 4);
		groups.invite(alice, "Alice", bob);
		UUID groupId = groups.groupOf(alice).id();
		assertEquals(Result.OK, groups.declineInvite(bob, groupId));
		assertEquals(Result.NO_SUCH_INVITE, groups.acceptInvite(bob, "Bob", groupId));
	}

	@Test
	void publicGroupJoinsInstantlyPrivateNeedsOwner() {
		aliceCreates(true, 4);
		assertEquals(Result.JOINED, groups.requestJoin(bob, "Bob", alice));
		groups.leave(bob);
		groups.disband(alice);

		assertEquals(Result.OK, groups.create(alice, "Alice", "Secret", false, 4));
		assertEquals(Result.REQUESTED, groups.requestJoin(bob, "Bob", alice));
		assertEquals(Result.ALREADY_REQUESTED, groups.requestJoin(bob, "Bob", alice));
		assertNull(groups.groupOf(bob));
		UUID groupId = groups.groupOf(alice).id();
		assertEquals(1, groups.requestsFor(groupId).size());
		assertEquals(Result.NOT_IN_GROUP, groups.respondRequest(bob, bob, true));
		assertEquals(Result.JOINED, groups.respondRequest(alice, bob, true));
		assertTrue(groups.groupOf(alice).isMember(bob));
	}

	@Test
	void ownerCanDeclineRequests() {
		aliceCreates(false, 4);
		groups.requestJoin(bob, "Bob", alice);
		assertEquals(Result.OK, groups.respondRequest(alice, bob, false));
		assertNull(groups.groupOf(bob));
		assertEquals(Result.NO_SUCH_REQUEST, groups.respondRequest(alice, bob, true));
	}

	@Test
	void fullGroupsRejectNewMembers() {
		aliceCreates(true, 2);
		assertEquals(Result.JOINED, groups.requestJoin(bob, "Bob", alice));
		assertEquals(Result.GROUP_FULL, groups.requestJoin(carol, "Carol", alice));
		assertEquals(Result.GROUP_FULL, groups.invite(alice, "Alice", carol));
	}

	@Test
	void kickRemovesMemberButNotOwner() {
		aliceCreates(true, 4);
		groups.requestJoin(bob, "Bob", alice);
		assertEquals(Result.NOT_OWNER, groups.kick(bob, alice));
		assertEquals(Result.SELF_TARGET, groups.kick(alice, alice));
		assertEquals(Result.NOT_IN_YOUR_GROUP, groups.kick(alice, carol));
		assertEquals(Result.OK, groups.kick(alice, bob));
		assertNull(groups.groupOf(bob));
		assertNull(groups.talkGroup(bob));
	}

	@Test
	void ownerLeavingPassesOwnershipAndLastLeaverDeletesGroup() {
		aliceCreates(true, 4);
		groups.requestJoin(bob, "Bob", alice);
		assertEquals(Result.OK, groups.leave(alice));
		assertEquals(bob, groups.groupOf(bob).owner());
		assertEquals(Result.OK, groups.leave(bob));
		assertTrue(groups.groups().isEmpty());
		assertNull(groups.findByName("Team"));
	}

	@Test
	void disbandClearsEverythingIncludingSpeakers() {
		aliceCreates(true, 4);
		groups.requestJoin(bob, "Bob", alice);
		groups.joinSpeaker(3, groups.groupOf(alice).id());
		assertEquals(Result.NOT_OWNER, groups.disband(bob));
		assertEquals(Result.OK, groups.disband(alice));
		assertNull(groups.groupOf(alice));
		assertNull(groups.groupOf(bob));
		assertNull(groups.speakerGroup(3));
	}

	@Test
	void nameColorOnlyVisibleWhileInGroup() {
		assertEquals(Result.NOT_IN_GROUP, groups.setNameColor(alice, 4));
		aliceCreates(true, 4);
		assertEquals(Result.BAD_COLOR, groups.setNameColor(alice, 16));
		assertEquals(Result.OK, groups.setNameColor(alice, 12));
		assertEquals(12, groups.visibleNameColor(alice));
		groups.leave(alice);
		assertEquals(GroupManager.NO_COLOR, groups.visibleNameColor(alice));
	}

	@Test
	void unansweredRequestsExpire() {
		aliceCreates(false, 4);
		groups.invite(alice, "Alice", bob);
		UUID groupId = groups.groupOf(alice).id();
		now.addAndGet(GroupManager.REQUEST_TTL_MS + 1);
		assertTrue(groups.invitesFor(bob).isEmpty());
		assertEquals(Result.NO_SUCH_INVITE, groups.acceptInvite(bob, "Bob", groupId));
	}

	@Test
	void disconnectCleansUp() {
		aliceCreates(false, 4);
		groups.requestJoin(bob, "Bob", alice);
		groups.removePlayer(bob);
		assertTrue(groups.requestsFor(groups.groupOf(alice).id()).isEmpty());
		groups.removePlayer(alice);
		assertTrue(groups.groups().isEmpty());
	}

	@Test
	void speakersOnlyTrackExistingGroups() {
		aliceCreates(true, 4);
		UUID groupId = groups.groupOf(alice).id();
		groups.joinSpeaker(1, groupId);
		assertEquals(groupId, groups.speakerGroup(1));
		assertTrue(groups.leaveSpeaker(1));
		assertNull(groups.speakerGroup(1));
	}
}
