import { createContext, useContext } from 'react';

const EMPTY_PARTICIPANTS = [];

const ParticipantsContext = createContext(EMPTY_PARTICIPANTS);

export const ParticipantsProvider = ({ participants, children }) => (
  <ParticipantsContext.Provider value={participants || EMPTY_PARTICIPANTS}>
    {children}
  </ParticipantsContext.Provider>
);

export const useParticipants = () => useContext(ParticipantsContext);

export default ParticipantsContext;
