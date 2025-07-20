/*
 * Copyright 2025 Samitha Chathuranga
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
      } catch (err) {
        setSuggestions([]);
      } finally {
        setLoading(false);
      }
    }, 400)
    , []);


  const handleInputChange = (event, value) => {
    setQuery(value);
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
        border: '4px solid #c7e6d0ff', 
        borderRadius: '8px', 
        backgroundColor: '#fafafa', 
        padding: '24px',
        display: 'flex',
        justifyContent: 'center', 
        alignItems: 'center',
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
