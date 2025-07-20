import React from 'react';
import { Container, Box, Typography, TextField, CircularProgress, Autocomplete } from '@mui/material';
import { fetchAddressSuggestions } from './api';
import debounce from 'lodash.debounce';
import { useMemo } from 'react';

function App() {
  const [query, setQuery] = React.useState('');
  const [suggestions, setSuggestions] = React.useState([]);
  const [loading, setLoading] = React.useState(false);

  const debouncedGetSuggestions = useMemo(() =>
    debounce(async (input) => {
      if (!input) {
        setSuggestions([]);
        return;
      }
      setLoading(true);
      try {

        const response = await fetchAddressSuggestions(input);

        const raw = response.data?.addresses || [];
        const addresses = raw.map(item => item.FullAddress);
        setSuggestions(addresses);

        console.log("Set to sugestions status:" + addresses);
      } catch (err) {
        console.error("API error:", err);
        setSuggestions([]);
      } finally {
        setLoading(false);
      }
    }, 400)
    , []);


  const handleInputChange = (event, value) => {
    setQuery(value);
    console.log("setting query value:" + value);

    if (value.length >= 3) {
      debouncedGetSuggestions(value);
    } else {
      setSuggestions([]);
    }
  };

  return (
    <Box
      sx={{
        width: '75vw',
        height: '75vh',
        position: 'absolute',
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
        border: '4px solid #c7e6d0ff', // Thick gray border // #e6e8ed
        borderRadius: '8px',       // Optional: rounded corners
        backgroundColor: '#fafafa', // Optional: subtle background
        padding: '24px', // 👈 Add padding here
        display: 'flex',              // Flexbox enabled
        justifyContent: 'center',     // Center horizontally
        alignItems: 'center',         // Center vertically
      }}
    >
      <Container maxWidth="sm">
        <Typography fontSize='1em' gutterBottom align="center" color='#5f5f60ff' sx={{ mb: 4 }}>
          NEW ZEALAND ADDRESS CHECKER
        </Typography>

        <Autocomplete
          freeSolo
          filterOptions={(x) => x} 
          options={suggestions}
          getOptionLabel={(option) => option}
          inputValue={query}
          onInputChange={handleInputChange}
          loading={loading}
          renderInput={(params) => (
            <TextField
              {...params}
              label="Enter NZ address"
              variant="outlined"
              fullWidth
              autoComplete="off"
              InputProps={{
                ...params.InputProps,
                endAdornment: (
                  <>
                    {loading && <CircularProgress size={20} />}
                    {params.InputProps.endAdornment}
                  </>
                )
              }}
            />
          )}
        />

      </Container>
    </Box>
  );
}

export default App;
