package com.example.slagalicaapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalicaapp.data.models.FriendData;
import com.example.slagalicaapp.data.models.GameInvite;
import com.example.slagalicaapp.repositories.FriendsRepository;

import java.util.List;

public class FriendsViewModel extends ViewModel {

    private final FriendsRepository repo = new FriendsRepository();

    public void setOnline(boolean online)   { repo.setOnline(online); }
    public void setInGame(boolean inGame)   { repo.setInGame(inGame); }

    public LiveData<List<FriendData>> getFriends()                     { return repo.getFriends(); }
    public LiveData<List<FriendData>> searchByUsername(String q)       { return repo.searchByUsername(q); }
    public LiveData<Boolean>          addFriend(FriendData fd)         { return repo.addFriend(fd); }
    public LiveData<Boolean>          removeFriend(String uid)         { return repo.removeFriend(uid); }

    public LiveData<String>     sendGameInvite(String toUid, String toName,
                                               String gameType, String roomId) {
        return repo.sendGameInvite(toUid, toName, gameType, roomId);
    }
    public void                 updateInviteStatus(String id, String s) { repo.updateInviteStatus(id, s); }
    public LiveData<GameInvite> listenForIncomingInvites()              { return repo.listenForIncomingInvites(); }
    public LiveData<String>     watchInviteStatus(String id)            { return repo.watchInviteStatus(id); }
    public void                 stopListeningForInvites()               { repo.stopListeningForInvites(); }
    public void                 cleanupInvite(String id)                { repo.cleanupInvite(id); }
    public void                 cleanupRoom(String gameType, String roomId) { repo.cleanupRoom(gameType, roomId); }

    @Override
    protected void onCleared() {
        super.onCleared();
        repo.stopListeningForInvites();
    }
}
